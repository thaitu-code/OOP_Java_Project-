package vn.id.fitnessai.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import vn.id.fitnessai.dto.ExerciseLevelRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExerciseLevelServiceTest {
    final ExerciseRepository exercises = mock(ExerciseRepository.class);
    final ExerciseLevelRepository levels = mock(ExerciseLevelRepository.class);
    final ExerciseLevelService service = new ExerciseLevelService(exercises, levels);
    Exercise exercise = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Test"));
    ExerciseLevelRequest request() {
        var r = new ExerciseLevelRequest(); r.setName("  Tùy chọn  "); r.setSets(3); r.setReps(null);
        r.setSecondsPerSet(30); r.setRestSeconds(60); r.setEstimatedSeconds(210); return r;
    }
    @Test void createsCustomNamedLevelUnderExerciseLockAndKeepsNullableReps() {
        when(exercises.findByIdForUpdate(1)).thenReturn(Optional.of(exercise));
        when(levels.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = service.save(1, null, request());
        assertEquals("Tùy chọn", response.name()); assertNull(response.reps()); assertEquals(210, response.estimatedSeconds());
        var order = inOrder(exercises, levels); order.verify(exercises).findByIdForUpdate(1);
        order.verify(levels).existsByExercise_ExerciseIdAndNameIgnoreCase(1, "Tùy chọn");
    }
    @Test void rejectsDuplicateNamesWithoutWriting() {
        when(exercises.findByIdForUpdate(1)).thenReturn(Optional.of(exercise));
        when(levels.existsByExercise_ExerciseIdAndNameIgnoreCase(1, "Tùy chọn")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.save(1, null, request()));
        verify(levels, never()).saveAndFlush(any());
    }
    @Test void updateCannotTargetLevelOfAnotherExercise() {
        when(exercises.findByIdForUpdate(1)).thenReturn(Optional.of(exercise));
        assertThrows(ResponseStatusException.class, () -> service.save(1, 99, request()));
        verify(levels, never()).saveAndFlush(any());
    }
    @Test void deactivationDoesNotDeleteLevel() {
        var level = new ExerciseLevel(exercise, "Level", 3, null, 30, 60, 210);
        when(exercises.findByIdForUpdate(1)).thenReturn(Optional.of(exercise));
        when(levels.findByIdAndExercise_ExerciseId(2, 1)).thenReturn(Optional.of(level));
        when(levels.saveAndFlush(level)).thenReturn(level);
        assertFalse(service.status(1, 2, false).active()); verify(levels, never()).delete(any());
    }
}
