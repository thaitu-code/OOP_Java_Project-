package vn.id.fitnessai.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkoutAutomationSelectionTest {
    @Test void selectedLevelBelongsToExerciseAndIsSnapshottedBeforeStart() {
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var users = mock(UserRepository.class); var workouts = mock(WorkoutRepository.class);
            var exercises = mock(ExerciseRepository.class); var details = mock(WorkoutDetailRepository.class);
            var service = new WorkoutService(workouts, users, exercises, details, validation.getValidator());
            var access = mock(UserRepository.WorkoutAccess.class);
            when(access.getId()).thenReturn(1L); when(access.getEnabled()).thenReturn(true);
            when(users.findWorkoutAccessForUpdate("test")).thenReturn(Optional.of(access));
            User owner = new User(); owner.setId(1L);
            var workout = new Workout(owner, OffsetDateTime.now()); ReflectionTestUtils.setField(workout, "workoutId", 1);
            when(workouts.findByIdForUpdate(1)).thenReturn(Optional.of(workout));
            var exercise = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Test"));
            ReflectionTestUtils.setField(exercise, "exerciseId", 2);
            var level = new ExerciseLevel(exercise, "Custom", 3, null, 30, 60, 210);
            ReflectionTestUtils.setField(level, "id", 3); exercise.getLevels().add(level);
            when(exercises.findByIdForUpdate(2)).thenReturn(Optional.of(exercise));
            when(details.save(any())).thenAnswer(call -> call.getArgument(0));
            var request = new WorkoutDetailRequest(); request.setExerciseId(2);
            assertThrows(IllegalArgumentException.class, () -> service.addExerciseToWorkout("test", 1, request));
            request.setLevelId(99);
            assertThrows(IllegalArgumentException.class, () -> service.addExerciseToWorkout("test", 1, request));
            request.setLevelId(3); level.setActive(false);
            assertThrows(IllegalArgumentException.class, () -> service.addExerciseToWorkout("test", 1, request));
            level.setActive(true);
            var response = service.addExerciseToWorkout("test", 1, request).orElseThrow();
            assertEquals("Custom", response.getAutomation().levelName());
            assertEquals(140000, response.getAutomation().minimumMillis());
            assertEquals(60, response.getApprovedRestSecondsSnapshot()); assertNull(response.getStartedAt());
            assertEquals(30000, response.getAutomation().phaseRemainingMillis());
            assertNull(response.getAutomation().phaseEndsAt());
            assertFalse(response.getAutomation().canFinishEarly());
            verify(details, times(1)).save(any());
        }
    }
}
