package vn.id.fitnessai.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import java.math.BigDecimal;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkoutAutomationEntityTest {
    private final OffsetDateTime start = OffsetDateTime.parse("2026-11-02T10:00:00+07:00");
    private final Duration timeout = Duration.ofSeconds(30);
    private Exercise exercise;
    private WorkoutDetail planned(int sets, int seconds, int rest, int estimated) {
        User owner = new User(); owner.setId(1L);
        exercise = new Exercise("Squat", new BigDecimal("6"), BigDecimal.ONE, new ExerciseCategory("Group"));
        var level = new ExerciseLevel(exercise, "Tùy chọn", sets, 10, seconds, rest, estimated);
        var detail = new WorkoutDetail(new Workout(owner, start), exercise);
        detail.configureAutomation(level); detail.start(start); return detail;
    }

    @ParameterizedTest
    @CsvSource({"1,IN_PROGRESS,1000,0,1", "4,RESTING,4000,0,1", "5,RESTING,4000,1000,1",
            "6,IN_PROGRESS,4000,2000,2", "10,RESTING,8000,2000,2", "11,RESTING,8000,3000,2",
            "12,IN_PROGRESS,8000,4000,3", "16,IN_PROGRESS,12000,4000,3", "20,IN_PROGRESS,16000,4000,3"})
    void splitsHeartbeatExactlyAcrossMultipleSetAndRestBoundaries(int seconds, WorkoutDetailStatus status,
            long active, long rest, int set) {
        var d = planned(3, 4, 2, 18); var token = d.getTimerToken();
        assertTrue(d.recordHeartbeat(token, start.plusSeconds(seconds), timeout));
        assertEquals(status, d.getStatus()); assertEquals(active, d.getActiveDurationMillis());
        assertEquals(rest, d.getRestDurationMillis()); assertEquals(set, d.getCurrentSetNumber());
        assertEquals(token, d.getTimerToken(), "Automatic phase transitions do not rotate the controlling lease");
        d.recordHeartbeat(token, start.plusSeconds(seconds), timeout);
        assertEquals(active, d.getActiveDurationMillis()); assertEquals(rest, d.getRestDurationMillis());
        assertNull(d.getCompletedAt(), "No automatic completion, including after the full target");
    }

    @Test
    void earlyCompletionUsesExactTwoThirdsRoundedUpToMillisecondAndIgnoresLegacyMinimum() {
        var d = planned(1, 5, 1, 1); var token = d.getTimerToken();
        assertEquals(667, d.minimumRequiredMillis());
        assertThrows(IllegalStateException.class, () -> d.complete(token, start.plusNanos(666000000), timeout,
                new BigDecimal("60"), null, null, null));
        assertTrue(d.complete(token, start.plusNanos(667000000), timeout, new BigDecimal("60"), null, null, null));
        assertEquals(WorkoutDetailStatus.COMPLETED, d.getStatus()); assertNull(d.getTimerToken());
    }

    @Test
    void completionDuringRestIncludesRestForThresholdButNotCalories() {
        var d = planned(3, 4, 2, 6);
        assertTrue(d.complete(d.getTimerToken(), start.plusSeconds(5), timeout, new BigDecimal("60"), null, null, null));
        assertEquals(4000, d.getActiveDurationMillis()); assertEquals(1000, d.getRestDurationMillis());
        assertEquals(new BigDecimal("0.4"), d.getCaloriesBurned());
    }

    @Test
    void offlineActiveFreezesAndRecoveryDoesNotBackfillGap() {
        var d = planned(3, 4, 2, 18); var old = d.getTimerToken();
        assertFalse(d.recordHeartbeat(old, start.plusSeconds(31), timeout));
        assertEquals(WorkoutDetailStatus.FROZEN, d.getStatus()); assertEquals(0, d.getActiveDurationMillis());
        d.recoverTimer(start.plusSeconds(100)); assertNotEquals(old, d.getTimerToken());
        assertThrows(IllegalStateException.class, () -> d.recordHeartbeat(old, start.plusSeconds(101), timeout));
        d.recordHeartbeat(d.getTimerToken(), start.plusSeconds(110), timeout);
        assertEquals(8000, d.getActiveDurationMillis()); assertEquals(2000, d.getRestDurationMillis());
    }

    @Test
    void offlineRestCountsOnlyToDeadlineThenRecoveryStartsNextSetAtReturnTime() {
        var d = planned(3, 4, 2, 18);
        d.recordHeartbeat(d.getTimerToken(), start.plusSeconds(4), timeout);
        assertTrue(d.freezeIfTimedOut(start.plusSeconds(50), timeout));
        assertEquals(WorkoutDetailStatus.FROZEN, d.getStatus());
        assertEquals(4000, d.getActiveDurationMillis()); assertEquals(2000, d.getRestDurationMillis());
        d.recoverTimer(start.plusSeconds(100));
        assertEquals(2, d.getCurrentSetNumber()); assertEquals(start.plusSeconds(100), d.getLastRecordedAt());
        d.recordHeartbeat(d.getTimerToken(), start.plusSeconds(104), timeout);
        assertEquals(8000, d.getActiveDurationMillis()); assertEquals(2000, d.getRestDurationMillis());
    }

    @Test
    void recoverBeforeRestDeadlineKeepsRemainingRestThenAutomaticallyContinues() {
        var d = planned(3, 4, 10, 18);
        d.recordHeartbeat(d.getTimerToken(), start.plusSeconds(4), timeout);
        d.recoverTimer(start.plusSeconds(9));
        assertEquals(WorkoutDetailStatus.RESTING, d.getStatus()); assertEquals(5000, d.automaticPhaseRemainingMillis());
        d.recordHeartbeat(d.getTimerToken(), start.plusSeconds(15), timeout);
        assertEquals(2, d.getCurrentSetNumber()); assertEquals(5000, d.getActiveDurationMillis());
        assertEquals(10000, d.getRestDurationMillis());
    }

    @Test
    void pauseAndResumePreserveSetProgressWithoutCountingPausedTime() {
        var d = planned(3, 4, 2, 18);
        assertTrue(d.pause(d.getTimerToken(), start.plusSeconds(2), timeout));
        d.resumePaused(d.getTimerToken(), start.plusSeconds(100));
        d.recordHeartbeat(d.getTimerToken(), start.plusSeconds(102), timeout);
        assertEquals(WorkoutDetailStatus.RESTING, d.getStatus()); assertEquals(4000, d.getActiveDurationMillis());
    }

    @Test
    void pauseAtBoundaryDoesNotTurnAutomaticRestIntoActiveOnResume() {
        var d = planned(3, 4, 2, 18);
        assertFalse(d.pause(d.getTimerToken(), start.plusSeconds(4), timeout));
        assertEquals(WorkoutDetailStatus.RESTING, d.getStatus());
        assertThrows(IllegalStateException.class, () -> d.resumePaused(d.getTimerToken(), start.plusSeconds(5)));
    }

    @Test
    void masterEditsCannotChangeSelectedPlanAndManualRestCannotOverrideIt() {
        var d = planned(3, 4, 2, 18);
        assertThrows(IllegalStateException.class, () -> d.approveRestDuration(20));
        assertThrows(IllegalStateException.class, () -> d.startRest(d.getTimerToken(), start.plusSeconds(1), timeout));
        assertThrows(IllegalStateException.class, () -> d.configureAutomation(
                new ExerciseLevel(exercise, "Other", 9, null, 30, 60, 100)));
        assertEquals(18, d.getEstimatedSecondsSnapshot()); assertEquals(12000, d.minimumRequiredMillis());
    }

    @Test void selectedConfigurationIsUnchangedByLaterMasterEditsOrDeactivation() {
        User owner = new User(); owner.setId(1L);
        var e = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Group"));
        e.setMediaUrl("/exercise-media/00000000-0000-0000-0000-000000000001.gif");
        var level = new ExerciseLevel(e, "Original", 2, null, 10, 5, 30);
        var d = new WorkoutDetail(new Workout(owner, start), e); d.configureAutomation(level);
        level.configure("Changed", 9, 20, 100, 200, 900); level.setActive(false); e.setMediaUrl(null);
        d.start(start);
        assertEquals("Original", d.getLevelNameSnapshot()); assertEquals(2, d.getPlannedSetsSnapshot());
        assertEquals(30, d.getEstimatedSecondsSnapshot()); assertEquals(5, d.getApprovedRestSecondsSnapshot());
        assertTrue(d.getMediaUrlSnapshot().endsWith("000000000001.gif"));
    }
}
