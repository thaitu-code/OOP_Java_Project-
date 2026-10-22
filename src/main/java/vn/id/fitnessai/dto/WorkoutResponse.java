package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class WorkoutResponse {

    private Integer workoutId;
    private Long userId;

    private OffsetDateTime startTime;
    private OffsetDateTime endTime;

    private WorkoutStatus status;

    private BigDecimal totalDurationMinutes;
    private BigDecimal totalCaloriesBurned;
}
