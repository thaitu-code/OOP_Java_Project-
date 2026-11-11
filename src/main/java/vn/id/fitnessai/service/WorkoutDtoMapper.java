package vn.id.fitnessai.service;

import vn.id.fitnessai.dto.WorkoutDetailResponse;
import vn.id.fitnessai.dto.WorkoutResponse;
import vn.id.fitnessai.dto.WorkoutTimerResponse;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutDetailStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

final class WorkoutDtoMapper {
    private WorkoutDtoMapper() {
    }

    static WorkoutDetailResponse detail(WorkoutDetail detail) {
        WorkoutDetailResponse response = new WorkoutDetailResponse(
                detail.getDetailId(), detail.getWorkout().getWorkoutId(),
                detail.getExerciseIdSnapshot(), detail.getExerciseNameSnapshot(),
                detail.getCategoryIdSnapshot(), detail.getCategoryNameSnapshot(),
                detail.getMetValueSnapshot(), detail.getStatus(), detail.getDurationMinutes(),
                detail.getSets(), detail.getReps(), detail.getWeightKg(), detail.getCaloriesBurned(),
                time(detail.getStartedAt()), time(detail.getCompletedAt()),
                detail.getApprovedRestSecondsSnapshot(), detail.getMinDurationMinutesSnapshot(),
                detail.getActiveDurationMillis(), detail.getRestDurationMillis(), time(detail.getCancelledAt())
        );

        response.setPlannedSets(detail.getPlannedSetsSnapshot());
        response.setPlannedReps(detail.getPlannedRepsSnapshot());
        if (detail.isAutomatic()) {
            long total = detail.getActiveDurationMillis() + detail.getRestDurationMillis();
            long remaining = detail.automaticPhaseRemainingMillis();
            
            response.setAutomation(new vn.id.fitnessai.dto.WorkoutAutomationResponse(detail.getLevelIdSnapshot(),
                    detail.getLevelNameSnapshot(), detail.getPlannedSetsSnapshot(), detail.getPlannedRepsSnapshot(),
                    detail.getSecondsPerSetSnapshot(), detail.getEstimatedSecondsSnapshot(), detail.minimumRequiredMillis(),
                    detail.getCurrentSetNumber(), detail.getElapsedInSetMillis(), remaining,
                    detail.getLastRecordedAt() == null || remaining == 0 ? null
                            : time(detail.getLastRecordedAt().plusNanos(remaining * 1000000L)),
                    total >= detail.minimumRequiredMillis(), total >= detail.getEstimatedSecondsSnapshot() * 1000L,
                    detail.getMediaUrlSnapshot()));
        }
        return response;
    }

    static WorkoutTimerResponse timer(WorkoutDetail detail, boolean includeToken) {
        return new WorkoutTimerResponse(
                detail(detail), includeToken ? detail.getTimerToken() : null,
                time(detail.getLastRecordedAt()), time(detail.getLastHeartbeatAt()),
                detail.getActiveDurationMillis(), detail.getRestDurationMillis(),
                time(detail.getRestStartedAt()), time(detail.getRestEndsAt())
        );
    }

    static WorkoutResponse workout(Workout workout, List<WorkoutDetail> details) {
        BigDecimal duration = BigDecimal.ZERO;
        BigDecimal calories = BigDecimal.ZERO;
        for (WorkoutDetail detail : details) {
            if (detail.getStatus() == WorkoutDetailStatus.COMPLETED) {
                duration = duration.add(detail.getDurationMinutes());
                calories = calories.add(detail.getCaloriesBurned());
            }
        }
        return workout(workout, duration, calories);
    }

    static WorkoutResponse workout(Workout workout, BigDecimal duration, BigDecimal calories) {
        return new WorkoutResponse(workout.getWorkoutId(), workout.getUser().getId(),
                time(workout.getStartTime()), time(workout.getEndTime()), workout.getStatus(),
                duration, calories);
    }

    private static OffsetDateTime time(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.ofHours(7))
                .truncatedTo(ChronoUnit.MICROS);
    }
}
