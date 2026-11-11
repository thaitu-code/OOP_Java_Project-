package vn.id.fitnessai.service;

import org.junit.jupiter.api.Test;
import vn.id.fitnessai.dto.LogDetailRequest;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.MealType;
import vn.id.fitnessai.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiPlanApplyServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AiRecommendationRepository recommendations = mock(AiRecommendationRepository.class);
    private final AiMealPlanItemRepository meals = mock(AiMealPlanItemRepository.class);
    private final AiRecommendedExerciseRepository exercises = mock(AiRecommendedExerciseRepository.class);
    private final NutritionService nutrition = mock(NutritionService.class);
    private final LogDetailRepository loggedDetails = mock(LogDetailRepository.class);
    private final WorkoutService workout = mock(WorkoutService.class);
    private final AiPlanApplyService service = new AiPlanApplyService(users, recommendations, meals, exercises,
            nutrition, loggedDetails, workout);

    private AiRecommendation plan(long id, String type, LocalDate start) {
        User user = new User(); user.setId(15L); user.setUsername("tester");
        UserProfile profile = new UserProfile(); profile.setUser(user);
        AiRecommendation rec = new AiRecommendation();
        rec.setId(id); rec.setType(type); rec.setProfile(profile); rec.setWorkoutStartDate(start);
        when(users.findByUsernameForUpdate("tester")).thenReturn(Optional.of(user));
        when(users.findByUsername("tester")).thenReturn(Optional.of(user));
        when(recommendations.findById(id)).thenReturn(Optional.of(rec));
        return rec;
    }

    @Test void repeatedMealApplyDoesNotLogTheSamePlanItemTwice() {
        AiRecommendation plan = plan(32L, "MEAL_PLAN", null);
        var food = mock(Food.class); when(food.getFoodId()).thenReturn(5);
        AiMealPlanItem item = new AiMealPlanItem(); item.setId(77L); item.setRecommendation(plan);
        item.setFood(food); item.setGrams(new BigDecimal("150")); item.setMealType(MealType.LUNCH);
        item.setPlanDate(LocalDate.now(NutritionLog.VN_ZONE));
        when(meals.findByRecommendation_IdOrderByPlanDateAscIdAsc(32L)).thenReturn(List.of(item));
        when(loggedDetails.existsByAiMealPlanItemId(77L)).thenReturn(false, true);

        var first = service.logToday("tester", 32L);
        var second = service.logToday("tester", 32L);

        assertEquals(1, first.get("count"));
        assertEquals(0, second.get("count"));
        assertEquals(1, second.get("skipped"));
        verify(nutrition, times(1)).addFoodFromAi(eq("tester"), any(LogDetailRequest.class), eq(77L));
        verify(nutrition, never()).addFood(anyString(), any());
    }

    @Test void distinctPlanItemsAreBothLoggedEvenForTheSameFood() {
        AiRecommendation plan = plan(44L, "MEAL_PLAN", null);
        var food = mock(Food.class); when(food.getFoodId()).thenReturn(5);
        AiMealPlanItem first = new AiMealPlanItem(); first.setId(81L); first.setRecommendation(plan);
        first.setFood(food); first.setGrams(new BigDecimal("100")); first.setMealType(MealType.LUNCH);
        first.setPlanDate(LocalDate.now(NutritionLog.VN_ZONE));
        AiMealPlanItem second = new AiMealPlanItem(); second.setId(82L); second.setRecommendation(plan);
        second.setFood(food); second.setGrams(new BigDecimal("150")); second.setMealType(MealType.LUNCH);
        second.setPlanDate(LocalDate.now(NutritionLog.VN_ZONE));
        when(meals.findByRecommendation_IdOrderByPlanDateAscIdAsc(44L)).thenReturn(List.of(first, second));

        var result = service.logToday("tester", 44L);
        assertEquals(2, result.get("count"));
        verify(nutrition).addFoodFromAi(eq("tester"), any(LogDetailRequest.class), eq(81L));
        verify(nutrition).addFoodFromAi(eq("tester"), any(LogDetailRequest.class), eq(82L));
    }

    @Test void startTodayUsesWeekdayAndCopiesPlannedSetsAndReps() {
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE);
        AiRecommendation plan = plan(64L, "WORKOUT_PLAN", today);
        plan.setStatus("APPLIED");
        var exercise = mock(Exercise.class); when(exercise.getExerciseId()).thenReturn(18);
        when(exercise.getLevels()).thenReturn(List.of());
        AiRecommendedExercise item = new AiRecommendedExercise();
        item.setExercise(exercise); item.setDayOfWeek(today.getDayOfWeek().getValue());
        item.setPlannedSets(4); item.setPlannedReps(12);
        when(exercises.findByRecommendation_IdOrderByDayOfWeekAscPositionAsc(64L)).thenReturn(List.of(item));
        when(workout.getCurrentWorkout("tester")).thenReturn(Optional.empty());
        var mockWorkout = new vn.id.fitnessai.dto.WorkoutResponse(133, 15L, OffsetDateTime.now(),
            null, vn.id.fitnessai.enums.WorkoutStatus.IN_PROGRESS, BigDecimal.ZERO, BigDecimal.ZERO);
        when(workout.createWorkout("tester")).thenReturn(mockWorkout);
        when(workout.addExerciseToWorkout(eq("tester"), eq(133), any(WorkoutDetailRequest.class)))
                .thenReturn(Optional.of(mock(vn.id.fitnessai.dto.WorkoutDetailResponse.class)));

        var result = service.startTodayWorkout("tester", 64L);
        assertEquals(1, result.get("count"));
        var request = org.mockito.ArgumentCaptor.forClass(WorkoutDetailRequest.class);
        verify(workout).addExerciseToWorkout(eq("tester"), eq(133), request.capture());
        assertEquals(18, request.getValue().getExerciseId());
        assertEquals(4, request.getValue().getPlannedSets());
        assertEquals(12, request.getValue().getPlannedReps());
    }
}
