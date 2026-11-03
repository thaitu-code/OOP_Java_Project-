package vn.id.fitnessai.dto;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Getter;
import vn.id.fitnessai.enums.WorkoutDetailStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@RequiredArgsConstructor
public class WorkoutDetailResponse {
    @Setter private WorkoutAutomationResponse automation;

    private final Integer detailId;
    private final Integer workoutId;

    private final Integer exerciseIdSnapshot;
    private final String exerciseNameSnapshot;
    private final Integer categoryIdSnapshot;
    private final String categoryNameSnapshot;
    private final BigDecimal metValueSnapshot;

    private final WorkoutDetailStatus status;

    private final BigDecimal durationMinutes;
    private final Integer sets;
    private final Integer reps;
    private final BigDecimal weightKg;
    private final BigDecimal caloriesBurned;

    private final OffsetDateTime startedAt;
    private final OffsetDateTime completedAt;

    private final Integer approvedRestSecondsSnapshot;
    private final BigDecimal minDurationMinutesSnapshot;
    private final long activeDurationMillis;
    private final long restDurationMillis;
    private final OffsetDateTime cancelledAt;
}
