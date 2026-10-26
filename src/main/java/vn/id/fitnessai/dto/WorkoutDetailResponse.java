package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import vn.id.fitnessai.enums.WorkoutDetailStatus;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class WorkoutDetailResponse {

    private Integer detailId;
    private Integer workoutId;

    private Integer exerciseIdSnapshot;
    private String exerciseNameSnapshot;
    private Integer categoryIdSnapshot;
    private String categoryNameSnapshot;
    private BigDecimal metValueSnapshot;

    private WorkoutDetailStatus status;

    private BigDecimal durationMinutes;
    private Integer sets;
    private Integer reps;
    private BigDecimal weightKg;
    private BigDecimal caloriesBurned;
}
