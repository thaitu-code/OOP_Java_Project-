package vn.id.fitnessai.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WorkoutDetailRestTest {
    private static final OffsetDateTime START =
            OffsetDateTime.parse("2026-10-31T16:00:00+07:00");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @ParameterizedTest
    @ValueSource(ints = {1, 60, 179, 180, 300})
    void acceptsApprovedDurationIncludingBelowThreeMinutes(int seconds) {
        WorkoutDetail detail = newDetail();

        detail.approveRestDuration(seconds);

        assertEquals(Integer.valueOf(seconds), detail.getApprovedRestSecondsSnapshot());
        assertEquals(WorkoutDetailStatus.NOT_STARTED, detail.getStatus());
        assertNull(detail.getRestStartedAt());
        assertNull(detail.getRestEndsAt());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 301, Integer.MAX_VALUE})
    void rejectsInvalidApprovalWithoutOverwritingPreviousValue(Integer seconds) {
        WorkoutDetail detail = newDetail();
        detail.approveRestDuration(60);

        assertThrows(IllegalArgumentException.class,
                () -> detail.approveRestDuration(seconds));

        assertEquals(Integer.valueOf(60), detail.getApprovedRestSecondsSnapshot());
    }

    @Test
    void missingApprovalPreventsRestWithoutChangingTimer() {
        WorkoutDetail detail = startedDetail();
        UUID token = detail.getTimerToken();

        assertThrows(IllegalStateException.class,
                () -> detail.startRest(token, START.plusSeconds(10), TIMEOUT));

        assertNull(detail.getApprovedRestSecondsSnapshot());
        assertEquals(WorkoutDetailStatus.IN_PROGRESS, detail.getStatus());
        assertEquals(0L, detail.getActiveDurationMillis());
        assertEquals(START, detail.getLastRecordedAt());
        assertNull(detail.getRestEndsAt());
        assertEquals(token, detail.getTimerToken());
    }

    @Test
    void startsRestUsingStoredApprovalAndRecordsFinalActiveSegment() {
        WorkoutDetail detail = startedDetail();
        detail.approveRestDuration(120);

        assertTrue(detail.startRest(detail.getTimerToken(), START.plusSeconds(10), TIMEOUT));

        assertEquals(WorkoutDetailStatus.RESTING, detail.getStatus());
        assertEquals(START.plusSeconds(10), detail.getRestStartedAt());
        assertEquals(START.plusSeconds(130), detail.getRestEndsAt());
        assertEquals(10_000L, detail.getActiveDurationMillis());
        assertEquals(0L, detail.getRestDurationMillis());
    }

    @Test
    void rejectsWrongTokenWithoutStartingRestOrCountingTime() {
        WorkoutDetail detail = startedDetail();
        detail.approveRestDuration(60);
        UUID token = detail.getTimerToken();

        assertThrows(IllegalStateException.class,
                () -> detail.startRest(UUID.randomUUID(), START.plusSeconds(10), TIMEOUT));

        assertEquals(WorkoutDetailStatus.IN_PROGRESS, detail.getStatus());
        assertEquals(0L, detail.getActiveDurationMillis());
        assertNull(detail.getRestStartedAt());
        assertNull(detail.getRestEndsAt());
        assertEquals(token, detail.getTimerToken());
    }

    @Test
    void expiredHeartbeatFreezesInsteadOfStartingRest() {
        WorkoutDetail detail = startedDetail();
        detail.approveRestDuration(60);

        assertFalse(detail.startRest(detail.getTimerToken(), START.plusSeconds(31), TIMEOUT));

        assertEquals(WorkoutDetailStatus.FROZEN, detail.getStatus());
        assertEquals(0L, detail.getActiveDurationMillis());
        assertNull(detail.getRestStartedAt());
        assertNull(detail.getRestEndsAt());
        assertNull(detail.getTimerToken());
    }

    @Test
    void changedApprovalAppliesOnlyToNextRestInterval() {
        WorkoutDetail detail = startedDetail();
        detail.approveRestDuration(60);
        detail.startRest(detail.getTimerToken(), START.plusSeconds(10), TIMEOUT);

        detail.approveRestDuration(120);

        assertEquals(START.plusSeconds(70), detail.getRestEndsAt());
        detail.resumeAfterRest(detail.getTimerToken(), START.plusSeconds(80));
        assertTrue(detail.startRest(detail.getTimerToken(), START.plusSeconds(90), TIMEOUT));
        assertEquals(START.plusSeconds(210), detail.getRestEndsAt());
        assertEquals(60_000L, detail.getRestDurationMillis());
        assertEquals(20_000L, detail.getActiveDurationMillis());
    }

    @Test
    void offlineRecoveryCountsRemainingRestAndRequiresExplicitResume() {
        WorkoutDetail detail = startedDetail();
        detail.approveRestDuration(120);
        detail.startRest(detail.getTimerToken(), START.plusSeconds(10), TIMEOUT);
        UUID oldToken = detail.getTimerToken();

        detail.recoverTimer(START.plusSeconds(80));

        assertEquals(WorkoutDetailStatus.RESTING, detail.getStatus());
        assertEquals(70_000L, detail.getRestDurationMillis());
        assertEquals(START.plusSeconds(130), detail.getRestEndsAt());
        assertNotEquals(oldToken, detail.getTimerToken());
        assertThrows(IllegalStateException.class,
                () -> detail.resumeAfterRest(detail.getTimerToken(), START.plusSeconds(100)));

        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(200), TIMEOUT);
        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(210), TIMEOUT);

        assertEquals(WorkoutDetailStatus.RESTING, detail.getStatus());
        assertEquals(120_000L, detail.getRestDurationMillis());
        assertEquals(10_000L, detail.getActiveDurationMillis());
        assertEquals(START.plusSeconds(130), detail.getLastRecordedAt());

        UUID restingToken = detail.getTimerToken();
        detail.resumeAfterRest(restingToken, START.plusSeconds(220));

        assertEquals(WorkoutDetailStatus.IN_PROGRESS, detail.getStatus());
        assertNotEquals(restingToken, detail.getTimerToken());
        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(230), TIMEOUT);
        assertEquals(20_000L, detail.getActiveDurationMillis());
        assertEquals(120_000L, detail.getRestDurationMillis());
    }

    @Test
    void completionCountsRestForMinimumButUsesActiveTimeOnlyForCalories() {
        WorkoutDetail detail = completedDetail();

        assertEquals(WorkoutDetailStatus.COMPLETED, detail.getStatus());
        assertEquals(10_000L, detail.getActiveDurationMillis());
        assertEquals(60_000L, detail.getRestDurationMillis());
        assertEquals(new BigDecimal("1.2"), detail.getDurationMinutes());
        assertEquals(new BigDecimal("1.1"), detail.getCaloriesBurned());
    }

    @Test
    void completedDetailKeepsApprovedDurationAndResults() {
        WorkoutDetail detail = completedDetail();
        OffsetDateTime completedAt = detail.getCompletedAt();

        assertThrows(IllegalStateException.class, () -> detail.approveRestDuration(120));

        assertEquals(Integer.valueOf(60), detail.getApprovedRestSecondsSnapshot());
        assertEquals(completedAt, detail.getCompletedAt());
        assertEquals(new BigDecimal("1.1"), detail.getCaloriesBurned());
    }

    @ParameterizedTest
    @EnumSource(value = WorkoutStatus.class, names = {"COMPLETED", "CANCELLED"})
    void closedWorkoutCannotChangeApprovedDuration(WorkoutStatus status) {
        WorkoutDetail detail = newDetail();
        detail.approveRestDuration(60);
        detail.getWorkout().setStatus(status);

        assertThrows(IllegalStateException.class, () -> detail.approveRestDuration(120));

        assertEquals(Integer.valueOf(60), detail.getApprovedRestSecondsSnapshot());
    }

    private WorkoutDetail newDetail() {
        ExerciseCategory category = new ExerciseCategory("Nhóm kiểm thử");
        Exercise exercise = new Exercise("Bài kiểm thử", new BigDecimal("6.0"),
                new BigDecimal("1.0"), category);
        Workout workout = new Workout(new User(), START);
        return new WorkoutDetail(workout, exercise);
    }

    private WorkoutDetail startedDetail() {
        WorkoutDetail detail = newDetail();
        detail.start(START);
        return detail;
    }

    private WorkoutDetail completedDetail() {
        WorkoutDetail detail = startedDetail();
        detail.approveRestDuration(60);
        detail.startRest(detail.getTimerToken(), START.plusSeconds(10), TIMEOUT);
        assertTrue(detail.complete(detail.getTimerToken(), START.plusSeconds(80), TIMEOUT,
                new BigDecimal("65.0"), null, null, null));
        return detail;
    }
}
