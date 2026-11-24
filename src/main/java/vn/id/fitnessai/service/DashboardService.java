package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class DashboardService {
    private final AiAccess access;
    private final NutritionService nutrition;
    private final WorkoutService workouts;
    private final WorkoutDetailRepository details;
    private final AiRecommendationRepository plans;
    private final AiMealPlanItemRepository meals;
    private final AiScheduleService schedule;
    private final Clock nutritionClock;
    public record Day(LocalDate date, NutritionTotalsResponse nutrition, BigDecimal burnedCalories) {}
    public record Targets(Long planId, BigDecimal calories, BigDecimal protein, BigDecimal carbs, BigDecimal fat) {}
    public record NextWorkout(Long planId, LocalDate date, String title, boolean rest, List<String> exercises) {}
    public record Summary(LocalDate today, List<Day> days, Targets targets, WorkoutResponse currentWorkout, NextWorkout nextWorkout) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Summary summary(String username) {
        var user = access.require(username);
        LocalDate today = LocalDate.now(nutritionClock.withZone(NutritionLog.VN_ZONE));
        List<Day> days = new ArrayList<>();
        for (LocalDate date = today.minusDays(6); !date.isAfter(today); date = date.plusDays(1)) {
            days.add(new Day(date, nutrition.getDailyLog(username, date).getTotals(), details.sumCaloriesForDay(user.getId(),
                    date.atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime(), date.plusDays(1).atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime())));
        }
        Targets targets = null;
        for (var p : plans.findByProfile_User_IdAndTypeOrderByIdDesc(user.getId(), "MEAL_PLAN")) {
            // A saved draft-backed meal plan has been explicitly confirmed. A future plan must not supply today's targets.
            if (p.getSourceDraftId() == null || "REPLACED".equals(p.getStatus())) continue;
            if (meals.findByRecommendation_IdOrderByPlanDateAscIdAsc(p.getId()).stream().noneMatch(m -> today.equals(m.getPlanDate()))) continue;
            targets = new Targets(p.getId(), p.getTargetCalories(), p.getTargetProtein(), p.getTargetCarbs(), p.getTargetFat());break;
        }
        NextWorkout next = null;
        for (LocalDate date = today; date.isBefore(today.plusDays(7)); date = date.plusDays(1)) {
            var effective = schedule.effective(user.getId(), date);
            if (effective.isEmpty()) continue;
            boolean rest = schedule.explicitRest(effective.get(), date);
            var entries = schedule.entries(user.getId(), date);
            if (date.equals(today) && rest || !entries.isEmpty()) {
                next = new NextWorkout(effective.get().getId(), date, effective.get().getTitle(), rest,
                        entries.stream().map(e -> e.getExercise().getExerciseName()).toList());break;
            }
        }
        return new Summary(today, days, targets, workouts.getCurrentWorkout(username).orElse(null), next);
    }
}
