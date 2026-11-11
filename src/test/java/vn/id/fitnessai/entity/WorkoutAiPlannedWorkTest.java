package vn.id.fitnessai.entity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutAiPlannedWorkTest {
    private WorkoutDetail detail() {
        User user = new User(); user.setId(1L);
        Exercise exercise = new Exercise("Pushup", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Strength"));
        return new WorkoutDetail(new Workout(user, OffsetDateTime.now()), exercise);
    }

    @Test void manualExercisePreservesSuggestedSetsAndRepsWithoutFabricatingActualResults() {
        WorkoutDetail d = detail();
        d.applyPlannedWork(4, 12);
        assertEquals(4, d.getPlannedSetsSnapshot());
        assertEquals(12, d.getPlannedRepsSnapshot());
        assertNull(d.getSets());
        assertNull(d.getReps());
        assertFalse(d.isAutomatic());
    }

    @Test void automaticExerciseRecalculatesItsTargetForAiPlan() {
        WorkoutDetail d = detail();
        var level = new ExerciseLevel(d.getExercise(), "Beginner", 2, 10, 30, 10, 70);
        d.configureAutomation(level);
        d.applyPlannedWork(4, 12);
        assertTrue(d.isAutomatic());
        assertEquals(4, d.getPlannedSetsSnapshot());
        assertEquals(12, d.getPlannedRepsSnapshot());
        assertEquals(150, d.getEstimatedSecondsSnapshot()); // 4*30+3*10 seconds
        assertEquals(100000L, d.minimumRequiredMillis());
    }

    @Test void invalidPlannedWorkDoesNotStartTheTimer() {
        WorkoutDetail d = detail();
        assertThrows(IllegalArgumentException.class, () -> d.applyPlannedWork(0, 12));
        assertThrows(IllegalArgumentException.class, () -> d.applyPlannedWork(3, 201));
        assertFalse(d.isAutomatic());
    }
}
