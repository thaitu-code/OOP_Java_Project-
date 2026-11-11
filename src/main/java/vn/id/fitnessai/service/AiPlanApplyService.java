package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.time.*;
import java.util.*;

/** Explicit user-triggered conversion of a saved recommendation into today's actual records. */
@Service @RequiredArgsConstructor
public class AiPlanApplyService {
    private final UserRepository users;
    private final AiRecommendationRepository recommendations;
    private final AiMealPlanItemRepository meals;
    private final AiRecommendedExerciseRepository exercises;
    private final NutritionService nutrition;
    private final LogDetailRepository loggedDetails;
    private final WorkoutService workout;

    private AiRecommendation own(String username, Long id, String expectedType) {
        var user=users.findByUsername(username).orElseThrow(()->new SecurityException("Tài khoản không tồn tại"));
        var rec=recommendations.findById(id).orElseThrow(()->new NoSuchElementException("Không tìm thấy kế hoạch"));
        if(!rec.getProfile().getUser().getId().equals(user.getId()))throw new SecurityException("Kế hoạch không thuộc bạn");
        if(!expectedType.equals(rec.getType()))throw new IllegalArgumentException("Sai loại kế hoạch");
        return rec;
    }

    @Transactional
    public Map<String,Object> logToday(String username, Long id) {
        // Serialize repeated/concurrent Apply requests for the same user.
        users.findByUsernameForUpdate(username)
            .orElseThrow(()->new SecurityException("Tài khoản không tồn tại"));
        own(username,id,"MEAL_PLAN");
        LocalDate today=LocalDate.now(NutritionLog.VN_ZONE);
        var items=meals.findByRecommendation_IdOrderByPlanDateAscIdAsc(id).stream().filter(m->today.equals(m.getPlanDate())).toList();
        if(items.isEmpty())throw new IllegalArgumentException("Không có món được lên kế hoạch cho hôm nay");
        // Each item is tracked individually: two identical foods in the same meal may both be intentional.
        int added=0;
        for(var item:items) {
            if(loggedDetails.existsByAiMealPlanItemId(item.getId()))continue;
            LogDetailRequest request=new LogDetailRequest();
            request.setFoodId(item.getFood().getFoodId());request.setMealType(item.getMealType());
            request.setWeightGrams(item.getGrams());request.setNote("Ghi từ thực đơn AI #"+id+" (người dùng xác nhận)");
            nutrition.addFoodFromAi(username,request,item.getId());
            added++;
        }
        int skipped=items.size()-added;
        return Map.of("message",added==0 ? "Các món hôm nay đã được ghi vào nhật ký; không thêm trùng."
                : "Đã ghi "+added+" món vào nhật ký (bỏ qua "+skipped+" món đã ghi).",
                "count",added,"skipped",skipped);
    }

    /** Exactly one active workout schedule per user; user-row lock serializes concurrent Apply requests. */
    @Transactional
    public Map<String,Object> applyWorkout(String username,Long id) {
        var user=users.findByUsernameForUpdate(username)
            .orElseThrow(()->new SecurityException("Tài khoản không tồn tại"));
        var rec=own(username,id,"WORKOUT_PLAN");
        var current=recommendations.findByProfile_User_IdAndTypeAndStatus(user.getId(),"WORKOUT_PLAN","APPLIED");
        for(var older:current) {
            if(!older.getId().equals(id))older.setStatus("REPLACED");
        }
        recommendations.saveAll(current);
        rec.setStatus("APPLIED");
        recommendations.save(rec);
        LocalDate today=LocalDate.now(NutritionLog.VN_ZONE);
        LocalDate start=planStart(rec);
        String message="Đã kích hoạt lịch #"+id+" và thay thế các lịch tập đang áp dụng trước đó. ";
        if(today.isBefore(start)) message+="Lịch bắt đầu vào "+start+"; chưa thêm bài vì chưa đến ngày tập.";
        else if(today.isAfter(start.plusDays(6))) message+="Tuần tập đã kết thúc; hãy tạo lịch mới.";
        else message+="Lịch đã kích hoạt. Hãy bấm 'Thêm bài hôm nay' nếu hôm nay có bài tập.";
        return Map.of("message",message,"status","APPLIED");
    }

    private LocalDate planStart(AiRecommendation rec) {
        LocalDate start=rec.getWorkoutStartDate();
        if(start!=null)return start;
        return rec.getCreatedAt().atZoneSameInstant(NutritionLog.VN_ZONE).toLocalDate()
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    /** Add today's plan to the user's ongoing workout, or start a new one if needed. */
    @Transactional
    public Map<String,Object> startTodayWorkout(String username,Long id) {
        // Same lock as Apply, so a replaced plan cannot create a new session concurrently.
        var user=users.findByUsernameForUpdate(username)
            .orElseThrow(()->new SecurityException("Tài khoản không tồn tại"));
        var rec=own(username,id,"WORKOUT_PLAN");
        if(!"APPLIED".equals(rec.getStatus()))
            throw new IllegalArgumentException("Lịch này không còn được kích hoạt. Hãy Apply lịch trước.");
        LocalDate today=LocalDate.now(NutritionLog.VN_ZONE);
        LocalDate start=planStart(rec);
        if(today.isBefore(start)||today.isAfter(start.plusDays(6)))
            throw new IllegalArgumentException("Hôm nay "+today+" nằm ngoài thời gian của lịch ("+start+" đến "+start.plusDays(6)+").");
        int day=today.getDayOfWeek().getValue();
        var items=exercises.findByRecommendation_IdOrderByDayOfWeekAscPositionAsc(id).stream()
            .filter(e->e.getDayOfWeek()==day).toList();
        if(items.isEmpty())throw new IllegalArgumentException("Hôm nay là ngày nghỉ / phục hồi, không có bài cần thêm.");

        var ongoing=workout.getCurrentWorkout(username);
        Integer workoutId=ongoing.map(WorkoutResponse::getWorkoutId).orElse(null);
        Set<Integer> existing=new HashSet<>();
        if(workoutId!=null) {
            for(var detail:workout.getWorkoutDetails(username,workoutId).orElse(List.of()))
                if(detail.getExerciseIdSnapshot()!=null)existing.add(detail.getExerciseIdSnapshot());
        }
        var toAdd=new ArrayList<AiRecommendedExercise>();
        for(var item:items) {
            Integer exerciseId=item.getExercise().getExerciseId();
            if(existing.add(exerciseId))toAdd.add(item);
        }
        if(toAdd.isEmpty())return Map.of("message","Các bài tập hôm nay đã có trong buổi tập, không thêm trùng.",
            "count",0,"workoutId",workoutId);
        if(workoutId==null)workoutId=workout.createWorkout(username).getWorkoutId();
        for(var item:toAdd) {
            WorkoutDetailRequest request=new WorkoutDetailRequest();
            request.setExerciseId(item.getExercise().getExerciseId());
            request.setPlannedSets(item.getPlannedSets());
            request.setPlannedReps(item.getPlannedReps());
            // Exercises with configured levels require a level ID in WorkoutService.
            var level=item.getExercise().getLevels().stream().filter(l->l.isActive()).findFirst();
            level.ifPresent(l->request.setLevelId(l.getId()));
            var added=workout.addExerciseToWorkout(username,workoutId,request);
            if(added.isEmpty())throw new IllegalStateException("Không thể thêm bài tập ID "+item.getExercise().getExerciseId());
        }
        return Map.of("message","Đã thêm "+toAdd.size()+" bài của hôm nay vào buổi tập #"+workoutId+" (bỏ qua bài trùng).",
            "workoutId",workoutId,"count",toAdd.size());
    }
}
