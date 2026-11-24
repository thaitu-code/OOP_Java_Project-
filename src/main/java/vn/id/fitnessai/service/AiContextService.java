package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.*;
import java.time.*;
import java.util.*;

/** Provider-facing projection. Never serialize account entities or timer DTOs. */
@Service @RequiredArgsConstructor
public class AiContextService {
    private final AiAccess access;
    private final UserProfileRepository profiles;
    private final BodyMetricsCalculator calculator;
    private final FoodRepository foods;
    private final ExerciseRepository exercises;
    private final NutritionService nutrition;
    private final WorkoutService workouts;
    private final UserStreakRepository streaks;
    private final AiAssistantService plans;
    private final AiScheduleService schedule;
    private final CheckInStreakService checkIns;

    @Transactional
    public Map<String,Object> context(String username) {
        User user = access.require(username);
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE);
        Map<String,Object> context = new LinkedHashMap<>();
        context.put("today", today); context.put("profile", profile(profiles.findByUserId(user.getId()).orElse(null)));
        var log = nutrition.getDailyLog(username, today);
        context.put("nutritionToday", Map.of("totals", log.getTotals(), "meals", log.getMeals().stream().map(m ->
                Map.of("mealType", m.getMealType(), "items", m.getItems().stream().map(i ->
                        Map.of("detailId", i.getDetailId(), "version", i.getVersion(), "foodId", i.getFoodId(), "name", i.getFoodNameSnapshot(), "grams", i.getWeightGrams(), "calories", i.getCalculatedCalories())).toList())).toList()));
        context.put("recentNutrition", nutrition.getLogHistory(username, 0, 7).getContent());
        context.put("currentWorkout", workouts.getCurrentWorkout(username).map(w -> Map.of("id", w.getWorkoutId(), "status", w.getStatus(),
                "items", workouts.getWorkoutDetails(username, w.getWorkoutId()).orElse(List.of()).stream().map(d -> {
                    Map<String,Object> item = new LinkedHashMap<>(); item.put("exerciseId", d.getExerciseIdSnapshot());
                    item.put("name", d.getExerciseNameSnapshot()); item.put("status", d.getStatus());
                    item.put("sets", d.getPlannedSets()); item.put("reps", d.getPlannedReps()); item.put("calories", d.getCaloriesBurned()); return item;
                }).toList())).orElse(Map.of()));
        context.put("recentWorkouts", workouts.getWorkouts(username, 0, 7, WorkoutStatus.COMPLETED).getContent().stream().map(w ->
                Map.of("date", w.getEndTime(), "calories", w.getTotalCaloriesBurned(), "minutes", w.getTotalDurationMinutes())).toList());
        context.put("streak", checkIns.getSummary(username));
        context.put("plans", plans.recommendations(username));
        context.put("effectiveWorkout", schedule.effective(user.getId(), today).map(p -> plans.detail(username, p.getId())).orElse(Map.of()));
        var meal = plans.recommendations(username).stream().filter(p -> "MEAL_PLAN".equals(p.type()) && !"REPLACED".equals(p.status())).findFirst();
        context.put("latestMealPlan", meal.map(p -> plans.detail(username, p.id())).orElse(Map.of()));
        context.put("foods", foods.findByActiveTrueAndCategory_ActiveTrueAndFoodNameContainingIgnoreCase("", PageRequest.of(0,120)).getContent().stream().map(f ->
                Map.of("id", f.getFoodId(), "name", f.getFoodName(), "calories", f.getCaloriesPer100g(), "protein", f.getProteinPer100g(), "carbs", f.getCarbsPer100g(), "fat", f.getFatPer100g())).toList());
        context.put("exercises", exercises.findAll().stream().filter(this::available).limit(150).map(e -> Map.of(
                "id", e.getExerciseId(), "name", e.getExerciseName(), "category", e.getCategory().getCategoryName(),
                "met", e.getMetValue(), "minimumMinutes", e.getMinDurationMinutes(), "levels", e.getLevels().stream().filter(ExerciseLevel::isActive).map(l -> {
                    Map<String,Object> level = new LinkedHashMap<>(); level.put("id", l.getId()); level.put("name", l.getName());
                    level.put("sets", l.getSets()); level.put("reps", l.getReps()); level.put("secondsPerSet", l.getSecondsPerSet()); level.put("restSeconds", l.getRestSeconds()); return level;
                }).toList())).toList());
        context.put("dataNotice", "Không có nhật ký chỉ có nghĩa chưa ghi nhận. Kho ứng viên có giới hạn; không có mục phù hợp thì hỏi lại, không bịa ID.");
        return context;
    }

    private boolean available(Exercise e) {
        return e.isActive() && e.getCategory().isActive() && (e.getLevels().isEmpty() || e.getLevels().stream().anyMatch(ExerciseLevel::isActive));
    }

    @Transactional(readOnly = true)
    public Map<String,Object> lookup(String username, tools.jackson.databind.JsonNode query) {
        access.require(username);
        String keyword = query.path("keyword").asText().trim(), kind = query.path("catalog").asText();
        if (keyword.isBlank() || keyword.length() > 80 || !Set.of("foods", "exercises").contains(kind)) throw new IllegalArgumentException("Yêu cầu tìm trong thư viện không hợp lệ");
        if (kind.equals("foods")) return Map.of("foods", foods.findByActiveTrueAndCategory_ActiveTrueAndFoodNameContainingIgnoreCase(keyword, PageRequest.of(0,30)).getContent().stream()
                .map(f -> Map.of("id", f.getFoodId(), "name", f.getFoodName(), "calories", f.getCaloriesPer100g(), "protein", f.getProteinPer100g(), "carbs", f.getCarbsPer100g(), "fat", f.getFatPer100g())).toList());
        String key = fold(keyword);
        return Map.of("exercises", exercises.findAll().stream().filter(this::available).filter(e -> fold(e.getExerciseName()).contains(key)).limit(30)
                .map(e -> Map.of("id", e.getExerciseId(), "name", e.getExerciseName(), "levels", e.getLevels().stream().filter(ExerciseLevel::isActive).map(l -> {
                    Map<String,Object> level = new LinkedHashMap<>(); level.put("id",l.getId());level.put("name",l.getName());level.put("sets",l.getSets());level.put("reps",l.getReps());level.put("secondsPerSet",l.getSecondsPerSet());return level;
                }).toList())).toList());
    }
    private String fold(String value) { return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('đ','d'); }

    public Map<String,Object> profile(UserProfile p) {
        Map<String,Object> out = new LinkedHashMap<>();
        if (p == null) return out;
        out.put("age", p.getBirthDate() == null ? null : calculator.calculateAge(p.getBirthDate()));
        out.put("gender", p.getGender()); out.put("heightCm", p.getHeightCm()); out.put("weightKg", p.getWeightKg());
        out.put("activityLevel", p.getActivityLevel()); out.put("goal", p.getGoal()); out.put("tdee", p.getCalculatedTdee());
        out.put("targetWeightKg", p.getTargetWeightKg()); out.put("trainingPreferences", p.getTrainingPreferences()); return out;
    }
}
