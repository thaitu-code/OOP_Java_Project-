package vn.id.fitnessai.service;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.entity.ExerciseCategory;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExerciseDeletionServiceTest {
    final ExerciseRepository exercises = mock(ExerciseRepository.class);
    final WorkoutDetailRepository details = mock(WorkoutDetailRepository.class);
    final ExerciseService service = new ExerciseService(exercises, mock(ExerciseCategoryRepository.class),
            details, mock(Validator.class));
    final Exercise exercise = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Group"));

    @Test void invalidIdNeverReadsOrWrites() {
        for (Integer id : new Integer[]{null, 0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> service.deleteExercise(id));
        }
        verifyNoInteractions(exercises, details);
    }

    @Test void missingExerciseReturnsFalseWithoutTouchingHistory() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.empty());
        assertFalse(service.deleteExercise(7));
        verify(exercises).findByIdForUpdate(7);
        verifyNoMoreInteractions(exercises);
        verifyNoInteractions(details);
    }

    @Test void referenceFromAnyOpenWorkoutPreventsDetachingAndDeleting() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.of(exercise));
        when(details.existsByExercise_ExerciseIdAndWorkout_Status(7, WorkoutStatus.IN_PROGRESS)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.deleteExercise(7));
        verify(details, never()).detachExerciseReferences(any());
        verify(exercises, never()).delete(any());
        verify(exercises, never()).flush();
    }

    @Test void detachesHistoryUnderExerciseLockBeforeDeletingAndFlushing() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.of(exercise));
        when(details.detachExerciseReferences(7)).thenReturn(3);
        assertTrue(service.deleteExercise(7));
        var order = inOrder(exercises, details);
        order.verify(exercises).findByIdForUpdate(7);
        order.verify(details).existsByExercise_ExerciseIdAndWorkout_Status(7, WorkoutStatus.IN_PROGRESS);
        order.verify(details).detachExerciseReferences(7);
        order.verify(exercises).delete(exercise);
        order.verify(exercises).flush();
        verify(details, never()).delete(any());
        verify(details, never()).deleteAll();
    }

    @Test void unusedExerciseCanStillBeDeleted() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.of(exercise));
        assertTrue(service.deleteExercise(7));
        verify(exercises).delete(exercise);
        verify(exercises).flush();
    }

    @Test void aiPlanReferenceRequiresDeactivationInsteadOfBreakingTheForeignKey() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.of(exercise));
        when(exercises.isUsedByAiPlans(7)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.deleteExercise(7));
        verify(exercises, never()).delete(any()); verify(details, never()).detachExerciseReferences(any());
    }

    @Test void failedDetachStopsBeforeMasterDeletion() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.of(exercise));
        var failure = new DataIntegrityViolationException("Test conflict");
        when(details.detachExerciseReferences(7)).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.deleteExercise(7)));
        verify(exercises, never()).delete(any());
    }

    @Test void failedDeleteFlushIsPropagatedForTransactionRollback() {
        when(exercises.findByIdForUpdate(7)).thenReturn(Optional.of(exercise));
        var failure = new DataIntegrityViolationException("Test conflict");
        doThrow(failure).when(exercises).flush();
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.deleteExercise(7)));
    }
}
