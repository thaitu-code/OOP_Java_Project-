package vn.id.fitnessai.service;

import org.junit.jupiter.api.Test;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiScheduleServiceTest {
    private final AiRecommendationRepository plans=mock(AiRecommendationRepository.class);
    private final AiRecommendedExerciseRepository items=mock(AiRecommendedExerciseRepository.class);
    private final WorkoutDetailRepository details=mock(WorkoutDetailRepository.class);
    private final AiScheduleService service=new AiScheduleService(plans,items,details);
    private AiRecommendation plan(long id, LocalDate start, String status) {
        var p=new AiRecommendation();p.setId(id);p.setWorkoutStartDate(start);p.setStatus(status);p.setType("WORKOUT_PLAN");return p;
    }
    @Test void scheduleRepeatsPastItsFirstWeekAndFutureScheduleStartsOnlyWhenEffective() {
        var start=LocalDate.of(2026,11,2);var old=plan(1,start,"APPLIED");var next=plan(2,start.plusDays(21),"SCHEDULED");old.setEffectiveUntil(start.plusDays(20));
        when(plans.findByProfile_User_IdAndTypeOrderByIdDesc(9L,"WORKOUT_PLAN")).thenReturn(List.of(next,old));
        assertEquals(1L,service.effective(9L,start.plusDays(15)).orElseThrow().getId());
        assertEquals(2L,service.effective(9L,start.plusDays(21)).orElseThrow().getId());
    }
    @Test void temporaryOverrideDoesNotAffectNextWeekOrAnUnrelatedBase() {
        var day=LocalDate.of(2026,11,12);var base=plan(1,day.minusDays(7),"APPLIED");var override=plan(2,day,"APPLIED");
        override.setParentRecommendationId(1L);override.setWorkoutOverrideDate(day);override.setRestDays(String.valueOf(day.getDayOfWeek().getValue()));
        when(plans.findByProfile_User_IdAndTypeOrderByIdDesc(9L,"WORKOUT_PLAN")).thenReturn(List.of(override,base));
        assertTrue(service.isRestDay(9L,day));assertFalse(service.isRestDay(9L,day.plusWeeks(1)));
        override.setParentRecommendationId(999L);assertFalse(service.isRestDay(9L,day));
    }
    @Test void emptyLegacyScheduleOrUnacceptedProposalIsNotAValidRestDay() {
        var day=LocalDate.of(2026,11,12);var p=plan(1,day,"PROPOSED");p.setRestDays("4");
        when(plans.findByProfile_User_IdAndTypeOrderByIdDesc(9L,"WORKOUT_PLAN")).thenReturn(List.of(p));
        assertFalse(service.isRestDay(9L,day));p.setStatus("APPLIED");p.setRestDays(null);assertFalse(service.isRestDay(9L,day));
    }
    @Test void restCannotBeGrantedAfterStartingEvenIfTheWorkoutWasLaterCancelled() {
        var today=LocalDate.now(NutritionLog.VN_ZONE);
        when(details.existsByWorkout_User_IdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(9L),any(),any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->service.requireCanRest(9L,today,today));
        assertThrows(IllegalArgumentException.class,()->service.requireCanRest(9L,today.minusDays(1),today));
        assertDoesNotThrow(()->service.requireCanRest(9L,today.plusDays(1),today));
    }
}
