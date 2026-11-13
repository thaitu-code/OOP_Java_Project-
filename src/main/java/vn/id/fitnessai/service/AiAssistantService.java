package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.math.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final AiChatMessageRepository chats;
    private final AiRecommendationRepository recommendations;
    private final AiMealPlanItemRepository mealItems;
    private final AiRecommendedExerciseRepository exerciseItems;
    private final FoodRepository foods;
    private final ExerciseRepository exercises;

    private final LogDetailRepository loggedDetails;
    private final AiActionDraftRepository drafts;


    private User own(String username) {
        User u = users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));
        if (!u.isEnabled() || u.isLocked()) {
            throw new SecurityException("Tài khoản không khả dụng");
        }
        return u;
    }

    private UserProfile profile(User u) {
        return profiles.findByUserId(u.getId()).orElseThrow(() -> new IllegalArgumentException("Cần cập nhật hồ sơ trước khi tạo khuyến nghị"));
    }

    public record Message(Long id, String sessionId, UUID turnId, String sender, String content, String status, OffsetDateTime createdAt) {
    }

    private Message map(AiChatMessage m) {
        return new Message(m.getId(), m.getSessionId(), m.getTurnId(), m.getSender(), m.getContent(), m.getStatus(), m.getCreatedAt());
    }

    public record ChatPage(List<Message> messages, Long nextCursor) {
    }

    @Transactional(readOnly = true)
    public ChatPage history(String username, Long before) {
        User u = own(username);
        long cursor = before == null ? Long.MAX_VALUE : before;
        List<AiChatMessage> list = chats.findByUser_IdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(u.getId(), cursor, PageRequest.of(0, 11));
        boolean more = list.size()>10;
        List<AiChatMessage> slice = list.subList(0, Math.min(10, list.size()));
        Long next = more ? slice.get(slice.size()-1).getId() : null;
        List<Message> out = new ArrayList<>(slice.stream().map(this::map).toList());
        Collections.reverse(out);
        return new ChatPage(out, next);
    }

    @Transactional
    public void delete(String username, Long id) {
        User u = users.findByUsernameForUpdate(username).orElseThrow(() -> new SecurityException("Không tìm thấy tài khoản"));
        if (!u.isEnabled() || u.isLocked()) throw new SecurityException("Tài khoản không khả dụng");
        AiChatMessage m = chats.findById(id).orElseThrow(() -> new NoSuchElementException("Không tìm thấy tin"));
        if (!m.getUser().getId().equals(u.getId())) {
            throw new SecurityException("Không được xóa tin người khác");
        }
        m.setDeletedAt(OffsetDateTime.now());
        for (var draft : drafts.findByUser_IdAndTurnIdOrderByCreatedAtAsc(u.getId(), m.getTurnId())) {
            if (draft.getStatus().equals("PENDING")) { draft.setStatus("CANCELLED"); draft.setResult("Đã xóa tin liên quan; đề xuất chưa thực hiện được hủy."); }
        }
    }

    public record RecommendationView(Long id, String type, String title, String content, String status, boolean read,
            OffsetDateTime createdAt, LocalDate workoutStartDate, long version, LocalDate effectiveUntil,
            LocalDate overrideDate, Long parentId, String restDays, BigDecimal targetCalories,
            BigDecimal targetProtein, BigDecimal targetCarbs, BigDecimal targetFat) {
    }

    private LocalDate workoutStart(AiRecommendation x) {
        if (x.getWorkoutStartDate() != null)return x.getWorkoutStartDate();
        LocalDate created = x.getCreatedAt().atZoneSameInstant(NutritionLog.VN_ZONE).toLocalDate();
        return created.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private RecommendationView view(AiRecommendation x) {
        return new RecommendationView(x.getId(), x.getType(), x.getTitle(), x.getContent(), x.getStatus(), x.getReadAt() != null,
                x.getCreatedAt(), "WORKOUT_PLAN".equals(x.getType()) ? workoutStart(x) : null, x.getVersion(),
                x.getEffectiveUntil(), x.getWorkoutOverrideDate(), x.getParentRecommendationId(), x.getRestDays(),
                x.getTargetCalories(), x.getTargetProtein(), x.getTargetCarbs(), x.getTargetFat());
    }

    @Transactional(readOnly = true)
    public List<RecommendationView> recommendations(String username) {
        return recommendations.findTop100ByProfile_User_IdOrderByIdDesc(own(username).getId()).stream().map(this::view).toList();
    }

    private static final Set<String> TYPES = Set.of("MEAL_PLAN", "WORKOUT_PLAN");

    public record MealItem(Integer foodId, LocalDate date, vn.id.fitnessai.enums.MealType mealType, BigDecimal grams) {
    }

    public record ExerciseItem(Integer exerciseId, Integer dayOfWeek, Integer sets, Integer reps, Integer levelId) {
        public ExerciseItem(Integer exerciseId, Integer dayOfWeek, Integer sets, Integer reps) {
            this(exerciseId, dayOfWeek, sets, reps, null);
        }
    }

    public record PlanRequest(String type, String title, String content, Long chatId, Integer mealLogId, BigDecimal targetCalories,
            List<MealItem> meals, List<ExerciseItem> exercises, LocalDate workoutStartDate, List<Integer> restDays,
            LocalDate overrideDate, Long parentId, BigDecimal targetProtein, BigDecimal targetCarbs, BigDecimal targetFat) {
        public PlanRequest(String type, String title, String content, Long chatId, Integer mealLogId, BigDecimal targetCalories,
                List<MealItem> meals, List<ExerciseItem> exercises, LocalDate workoutStartDate) {
            this(type, title, content, chatId, mealLogId, targetCalories, meals, exercises, workoutStartDate,
                    null, null, null, null, null, null);
        }
    }

    public record PlanResult(RecommendationView recommendation, BigDecimal calories, BigDecimal protein, BigDecimal carbs, BigDecimal fat, BigDecimal targetCalories) {
    }

    private BigDecimal calc(BigDecimal per100, BigDecimal grams) {
     return per100.multiply(grams).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    @Transactional
    public PlanResult create(String username, PlanRequest r) {
        return buildPlan(username, r, true);
    }

    @Transactional(readOnly = true)
    public PlanResult preview(String username, PlanRequest r) {
        return buildPlan(username, r, false);
    }

    private PlanResult buildPlan(String username, PlanRequest r, boolean persist) {
        User u = own(username);
        UserProfile p = profile(u);
        if (r == null || r.type() == null || !TYPES.contains(r.type())) {
            throw new IllegalArgumentException("Loại khuyến nghị không hợp lệ");
        }
        if (r.title() == null || r.title().isBlank() || r.title().length()>180 || r.content() == null || r.content().isBlank() || r.content().length()>10000) {
            throw new IllegalArgumentException("Tiêu đề/nội dung không hợp lệ");
        }
        if (!"MEAL_PLAN".equals(r.type()) && r.meals() != null && !r.meals().isEmpty()) {
            throw new IllegalArgumentException("Danh sách món chỉ dành cho MEAL_PLAN");
        }
        if (!"WORKOUT_PLAN".equals(r.type()) && r.exercises() != null && !r.exercises().isEmpty()) {
            throw new IllegalArgumentException("Danh sách bài chỉ dành cho WORKOUT_PLAN");
        }
        if (r.mealLogId() != null) {
            throw new IllegalArgumentException("Không cho gắn meal_log_id khi tạo kế hoạch dự kiến; nhật ký thực tế được quản lý riêng");
        }
        AiRecommendation a = new AiRecommendation();
        a.setProfile(p);
        a.setType(r.type());
        a.setTitle(r.title());
        a.setContent(r.content());
        a.setParentRecommendationId(r.parentId());
        a.setTargetCalories(r.targetCalories());
        a.setTargetProtein(r.targetProtein());
        a.setTargetCarbs(r.targetCarbs());
        a.setTargetFat(r.targetFat());
        for (BigDecimal target : Arrays.asList(r.targetProtein(), r.targetCarbs(), r.targetFat())) {
            if (target != null && (target.signum() < 0 || target.stripTrailingZeros().scale() > 2 || target.compareTo(new BigDecimal("5000")) > 0))
                throw new IllegalArgumentException("Mục tiêu dinh dưỡng không hợp lệ");
        }
        if (r.parentId() != null) {
            var parent = recommendations.findById(r.parentId()).orElseThrow(() -> new IllegalArgumentException("Kế hoạch gốc không tồn tại"));
            if (!parent.getProfile().getUser().getId().equals(u.getId()) || !parent.getType().equals(r.type()))
                throw new SecurityException("Kế hoạch gốc không thuộc bạn hoặc sai loại");
        }
        if ("WORKOUT_PLAN".equals(r.type())) {
            LocalDate now = LocalDate.now(NutritionLog.VN_ZONE);
            LocalDate start = r.workoutStartDate() == null ? now : r.workoutStartDate();
            if (start.isBefore(now) || start.isAfter(now.plusDays(90))) {
             throw new IllegalArgumentException("Ngày bắt đầu lịch tập phải từ hôm nay đến 90 ngày tới");
            }
            a.setWorkoutStartDate(start);
            a.setWorkoutOverrideDate(r.overrideDate());
            if (r.overrideDate() != null && (!r.overrideDate().equals(start) || r.parentId() == null))
                throw new IllegalArgumentException("Điều chỉnh ngày cần lịch gốc và ngày bắt đầu tương ứng");
        } else if (r.workoutStartDate() != null) {
            throw new IllegalArgumentException("Chỉ lịch tập mới có ngày bắt đầu");
        }
        if (r.chatId() != null) {
            AiChatMessage m = chats.findById(r.chatId()).orElseThrow(() -> new IllegalArgumentException("Chat không tồn tại"));
            if (!m.getUser().getId().equals(u.getId()) || m.getDeletedAt() != null) {
                throw new SecurityException("Chat không thuộc user");
            }
            a.setChat(m);
        }
        if (persist) a = recommendations.save(a);
        BigDecimal k = BigDecimal.ZERO, pr = k, ca = k, fa = k;
        if ("MEAL_PLAN".equals(r.type())) {
            if (r.meals() == null || r.meals().isEmpty() || r.meals().size()>60) {
                throw new IllegalArgumentException("Thực đơn cần 1-60 món");
            }
            if (r.targetCalories() == null || r.targetCalories().signum() <= 0 || r.targetCalories().stripTrailingZeros().scale() > 2 || r.targetCalories().compareTo(new BigDecimal("10000")) > 0) {
                throw new IllegalArgumentException("Thiếu mục tiêu calo");
            }
            for (MealItem item : r.meals()) {
                if (item == null || item.foodId() == null || item.date() == null || item.mealType() == null || item.grams() == null || item.grams().signum() <= 0 || item.grams().stripTrailingZeros().scale() > 2 || item.grams().compareTo(new BigDecimal("5000"))>0 || item.date().isBefore(LocalDate.now(NutritionLog.VN_ZONE)) || item.date().isAfter(LocalDate.now(NutritionLog.VN_ZONE).plusDays(90))) {
                    throw new IllegalArgumentException("Ngày, bữa hoặc số gram không hợp lệ");
                }
                Food food = foods.findById(item.foodId()).orElseThrow(() -> new IllegalArgumentException("Food ID không tồn tại"));
                if (!food.isAvailableForLogging()) {
                    throw new IllegalArgumentException("Food ID không hoạt động");
                }
                AiMealPlanItem i = new AiMealPlanItem();
                i.setRecommendation(a);
                i.setFood(food);
                i.setPlanDate(item.date());
                i.setMealType(item.mealType());
                i.setGrams(item.grams());
                i.setCalories(calc(food.getCaloriesPer100g(), item.grams()));
                i.setProtein(calc(food.getProteinPer100g(), item.grams()));
                i.setCarbs(calc(food.getCarbsPer100g(), item.grams()));
                i.setFat(calc(food.getFatPer100g(), item.grams()));
                if (persist) mealItems.save(i);
                k = k.add(i.getCalories());
                pr = pr.add(i.getProtein());
                ca = ca.add(i.getCarbs());
                fa = fa.add(i.getFat());
            }
        }
        if ("WORKOUT_PLAN".equals(r.type())) {
            if (r.exercises() == null || r.exercises().size()>80) {
                throw new IllegalArgumentException("Danh sách bài tập không hợp lệ");
            }
            Set<Integer> trainingDays = new HashSet<>();
            Set<Integer> rest = r.restDays() == null ? new TreeSet<>() : new TreeSet<>(r.restDays());
            if (rest.stream().anyMatch(d -> d == null || d < 1 || d > 7)) throw new IllegalArgumentException("Ngày nghỉ không hợp lệ");
            Set<String> seen = new HashSet<>();
            int pos = 0;
            for (ExerciseItem item : r.exercises()) {
             if (item == null || item.exerciseId() == null || item.dayOfWeek() == null || item.dayOfWeek()<1 || item.dayOfWeek()>7) {
                 throw new IllegalArgumentException("Bài tập/ngày/sets/reps không hợp lệ");
             }
             Exercise ex = exercises.findById(item.exerciseId()).orElseThrow(() -> new IllegalArgumentException("Exercise ID không tồn tại"));
             if (!ex.isActive()) {
                 throw new IllegalArgumentException("Bài tập đã ngưng sử dụng");
             }
             if (!seen.add(item.dayOfWeek() + ":" + item.exerciseId())) throw new IllegalArgumentException("Bài bị lặp trong cùng ngày");
             trainingDays.add(item.dayOfWeek());
             if (item.levelId() != null) {
                 var level = ex.getLevels().stream().filter(l -> l.isActive() && l.getId().equals(item.levelId()))
                         .findFirst().orElseThrow(() -> new IllegalArgumentException("Cấp độ không thuộc bài hoặc đã ngừng"));
                 if (!Objects.equals(item.sets(), level.getSets()) || !Objects.equals(item.reps(), level.getReps()))
                     throw new IllegalArgumentException("Chỉ tiêu phải khớp cấp độ đã chọn");
             } else if (!ex.getLevels().isEmpty()) {
                 throw new IllegalArgumentException("Cần chọn cấp độ cho từng bài");
             } else if (item.sets() == null || item.sets() < 1 || item.sets() > 30 || item.reps() == null || item.reps() < 1 || item.reps() > 200) {
                 throw new IllegalArgumentException("Bài thủ công cần số hiệp/lần hợp lệ");
             }
             AiRecommendedExercise i = new AiRecommendedExercise();
             i.setRecommendation(a);
             i.setExercise(ex);
             i.setDayOfWeek(item.dayOfWeek());
             i.setPlannedSets(item.sets());
             i.setPlannedReps(item.reps());
             i.setLevelId(item.levelId());
             i.setPosition(pos++);
             if (persist) exerciseItems.save(i);
            }
            if (r.restDays() == null) {
                for (int day = 1; day <= 7; day++) if (!trainingDays.contains(day)) rest.add(day);
            }
            if (!Collections.disjoint(rest, trainingDays)) throw new IllegalArgumentException("Một ngày không thể vừa tập vừa nghỉ");
            if (r.overrideDate() == null) {
                if (trainingDays.isEmpty() || trainingDays.size() + rest.size() != 7)
                    throw new IllegalArgumentException("Lịch tuần phải xác định đủ 7 ngày tập/nghỉ");
            } else {
                int day = r.overrideDate().getDayOfWeek().getValue();
                if (trainingDays.stream().anyMatch(d -> d != day) || rest.stream().anyMatch(d -> d != day)
                        || (!trainingDays.contains(day) && !rest.contains(day)))
                    throw new IllegalArgumentException("Điều chỉnh chỉ được chứa ngày đã chọn");
            }
            a.setRestDays(rest.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        }
        return new PlanResult(view(a), k, pr, ca, fa, r.targetCalories());
    }

    @Transactional
    public RecommendationView read(String username, Long id) {
        User u = own(username);
        AiRecommendation r = recommendations.findById(id).orElseThrow();
        if (!r.getProfile().getUser().getId().equals(u.getId())) {
            throw new SecurityException("Không có quyền");
        }
        r.setReadAt(OffsetDateTime.now());
        return view(r);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String username, Long id) {
        User u = own(username);
        AiRecommendation r = recommendations.findById(id).orElseThrow();
        if (!r.getProfile().getUser().getId().equals(u.getId())) {
            throw new SecurityException("Không có quyền");
        }
        List<Map<String,Object>> meals = mealItems.findByRecommendation_IdOrderByPlanDateAscIdAsc(id).stream().map(i -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", i.getId()); row.put("foodId", i.getFood().getFoodId()); row.put("foodName", i.getFood().getFoodName());
            row.put("date", i.getPlanDate()); row.put("mealType", i.getMealType()); row.put("grams", i.getGrams());
            row.put("calories", i.getCalories()); row.put("protein", i.getProtein()); row.put("carbs", i.getCarbs()); row.put("fat", i.getFat());
            row.put("logged", loggedDetails.existsByAiMealPlanItemId(i.getSourceItemId() == null ? i.getId() : i.getSourceItemId()));
            return row;
        }).toList();
        List<Map<String,Object>> entries = exerciseItems.findByRecommendation_IdOrderByDayOfWeekAscPositionAsc(id).stream().map(i -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("exerciseId", i.getExercise().getExerciseId()); row.put("exerciseName", i.getExercise().getExerciseName());
            row.put("dayOfWeek", i.getDayOfWeek()); row.put("sets", i.getPlannedSets()); row.put("reps", i.getPlannedReps()); row.put("levelId", i.getLevelId());
            i.getExercise().getLevels().stream().filter(l -> Objects.equals(l.getId(), i.getLevelId())).findFirst().ifPresent(l -> {
                row.put("levelName", l.getName()); row.put("secondsPerSet", l.getSecondsPerSet());
            });
            return row;
        }).toList();
        return Map.of("recommendation", view(r), "meals", meals, "exercises", entries);
    }
}
