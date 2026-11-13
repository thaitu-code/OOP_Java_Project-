package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiPlanApplyService {
    private final UserRepository users;
    private final AiRecommendationRepository recommendations;
    private final AiMealPlanItemRepository meals;
    private final AiRecommendedExerciseRepository exercises;
    private final NutritionService nutrition;
    private final LogDetailRepository loggedDetails;
    private final WorkoutService workout;
    private final AiScheduleService schedule;

    private User lock(String username) {
        var user = users.findByUsernameForUpdate(username).orElseThrow(() -> new SecurityException("Tài khoản không tồn tại"));
        if (!user.isEnabled() || user.isLocked()) throw new SecurityException("Tài khoản không khả dụng");
        return user;
    }
    private AiRecommendation own(User user, Long id, String type) {
        var rec = recommendations.findById(id).orElseThrow(() -> new NoSuchElementException("Không tìm thấy kế hoạch"));
        if (!rec.getProfile().getUser().getId().equals(user.getId())) throw new SecurityException("Kế hoạch không thuộc bạn");
        if (!type.equals(rec.getType())) throw new IllegalArgumentException("Sai loại kế hoạch");
        return rec;
    }
    public record MealSelection(Long id, BigDecimal grams) {}
    @Transactional
    public Map<String,Object> logToday(String username, Long id) { return logSelected(username, id, null); }
    @Transactional
    public Map<String,Object> logSelected(String username, Long id, List<MealSelection> selection) {
        User user = lock(username);
        var rec = own(user, id, "MEAL_PLAN");
        if ("REPLACED".equals(rec.getStatus())) throw new IllegalArgumentException("Thực đơn đã thay thế; hãy chọn phiên bản mới");
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE);
        var items = meals.findByRecommendation_IdOrderByPlanDateAscIdAsc(id).stream().filter(m -> today.equals(m.getPlanDate())).toList();
        if (items.isEmpty()) throw new IllegalArgumentException("Không có món được lên kế hoạch cho hôm nay");
        Map<Long,BigDecimal> selected = new HashMap<>();
        if (selection != null) {
            if (selection.isEmpty() || selection.size() > 60) throw new IllegalArgumentException("Hãy chọn 1-60 món đã ăn");
            for (var s : selection) {
                if (s == null || s.id() == null || s.grams() == null || s.grams().signum() <= 0 || s.grams().compareTo(new BigDecimal("5000")) > 0
                        || selected.put(s.id(), s.grams()) != null || items.stream().noneMatch(i -> i.getId().equals(s.id())))
                    throw new IllegalArgumentException("Món hoặc khối lượng không hợp lệ cho hôm nay");
            }
        }
        int added = 0, skipped = 0;
        for (var item : items) {
            if (selection != null && !selected.containsKey(item.getId())) continue;
            Long origin = item.getSourceItemId() == null ? item.getId() : item.getSourceItemId();
            if (loggedDetails.existsByAiMealPlanItemId(origin)) { skipped++; continue; }
            LogDetailRequest request = new LogDetailRequest();
            request.setFoodId(item.getFood().getFoodId()); request.setMealType(item.getMealType());
            request.setWeightGrams(selection == null ? item.getGrams() : selected.get(item.getId()));
            request.setNote("Người dùng xác nhận từ thực đơn #" + id);
            nutrition.addFoodFromAi(username, request, origin, today); added++;
        }
        return Map.of("message", "Đã ghi " + added + " món; bỏ qua " + skipped + " món đã ghi.", "count", added, "skipped", skipped);
    }
    @Transactional
    public Map<String,Object> applyWorkout(String username, Long id) {
        var user = lock(username);
        var rec = own(user, id, "WORKOUT_PLAN");
        if (Set.of("APPLIED", "SCHEDULED").contains(rec.getStatus()))
            return Map.of("message", "Lịch đã được chấp nhận; không áp dụng trùng.", "status", rec.getStatus());
        if (!"PROPOSED".equals(rec.getStatus())) throw new IllegalArgumentException("Lịch đã thay thế; hãy tạo phiên bản mới");
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE), start = AiScheduleService.start(rec);
        if (start.isBefore(today)) throw new IllegalArgumentException("Ngày bắt đầu đã qua. Hãy tạo bản xem trước với ngày mới");
        if (schedule.explicitRest(rec, start)) schedule.requireCanRest(user.getId(), start, today);
        if (rec.getWorkoutOverrideDate() != null) {
            var base = schedule.effective(user.getId(), start).orElseThrow(() -> new IllegalArgumentException("Chưa có lịch gốc hiệu lực"));
            Long baseId = base.getWorkoutOverrideDate() == null ? base.getId() : base.getParentRecommendationId();
            if (!Objects.equals(baseId, rec.getParentRecommendationId())) throw new IllegalArgumentException("Lịch gốc vừa thay đổi; hãy tạo lại đề xuất");
            for (var old : recommendations.findByProfile_User_IdAndTypeOrderByIdDesc(user.getId(), "WORKOUT_PLAN"))
                if (!old.getId().equals(id) && start.equals(old.getWorkoutOverrideDate()) && "APPLIED".equals(old.getStatus())) old.setStatus("REPLACED");
            rec.setStatus("APPLIED");
        } else {
            for (var old : recommendations.findByProfile_User_IdAndTypeOrderByIdDesc(user.getId(), "WORKOUT_PLAN")) {
                if (old.getId().equals(id) || old.getWorkoutOverrideDate() != null || !Set.of("APPLIED", "SCHEDULED").contains(old.getStatus())) continue;
                if (!AiScheduleService.start(old).isBefore(start)) {
                    old.setStatus("REPLACED"); old.setEffectiveUntil(AiScheduleService.start(old).minusDays(1));
                } else if (old.getEffectiveUntil() == null || !old.getEffectiveUntil().isBefore(start)) {
                    old.setEffectiveUntil(start.minusDays(1));
                    if (!start.isAfter(today)) old.setStatus("REPLACED");
                }
            }
            rec.setStatus(start.isAfter(today) ? "SCHEDULED" : "APPLIED");
        }
        recommendations.save(rec);
        if (!today.equals(LocalDate.now(NutritionLog.VN_ZONE))) throw new IllegalStateException("Đã sang ngày mới; hãy xem lại lịch trước khi áp dụng");
        return Map.of("message", "Đã chấp nhận lịch từ " + start + ". Lịch tuần lặp đến khi thay đổi; chưa thêm bài hoặc ghi nhận đã tập.", "status", rec.getStatus());
    }
    @Transactional
    public Map<String,Object> startTodayWorkout(String username, Long id) {
        var user = lock(username); own(user, id, "WORKOUT_PLAN");
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE);
        var current = schedule.effective(user.getId(), today).orElseThrow(() -> new IllegalArgumentException("Chưa có lịch hiệu lực hôm nay"));
        if (!current.getId().equals(id) && !Objects.equals(current.getParentRecommendationId(), id))
            throw new IllegalArgumentException("Lịch này không hiệu lực hôm nay");
        var items = schedule.entries(user.getId(), today);
        if (items.isEmpty()) throw new IllegalArgumentException("Hôm nay không có bài tập trong lịch hiệu lực");
        var ongoing = workout.getCurrentWorkout(username);
        Integer workoutId = ongoing.map(WorkoutResponse::getWorkoutId).orElse(null);
        Set<Integer> existing = new HashSet<>();
        if (workoutId != null) for (var d : workout.getWorkoutDetails(username, workoutId).orElse(List.of())) existing.add(d.getExerciseIdSnapshot());
        var toAdd = items.stream().filter(i -> existing.add(i.getExercise().getExerciseId())).toList();
        if (toAdd.isEmpty()) return Map.of("message", "Bài đã có trong buổi; giữ nguyên chỉ tiêu đã chọn.", "count", 0, "workoutId", workoutId);
        if (workoutId == null) workoutId = workout.createWorkout(username).getWorkoutId();
        for (var item : toAdd) {
            WorkoutDetailRequest request = new WorkoutDetailRequest();
            request.setExerciseId(item.getExercise().getExerciseId()); request.setLevelId(item.getLevelId());
            if (item.getLevelId() == null) { request.setPlannedSets(item.getPlannedSets()); request.setPlannedReps(item.getPlannedReps()); }
            if (workout.addExerciseToWorkout(username, workoutId, request).isEmpty()) throw new IllegalStateException("Bài tập vừa thay đổi; hãy tải lại lịch");
        }
        if (!today.equals(LocalDate.now(NutritionLog.VN_ZONE))) throw new IllegalStateException("Đã sang ngày mới; hãy tải lại lịch hôm nay");
        return Map.of("message", "Đã thêm " + toAdd.size() + " bài; bài đã có giữ nguyên chỉ tiêu. Chưa bắt đầu timer.", "count", toAdd.size(), "workoutId", workoutId);
    }
}
