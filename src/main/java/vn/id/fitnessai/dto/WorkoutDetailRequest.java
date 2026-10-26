package vn.id.fitnessai.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class WorkoutDetailRequest {

    private Integer exerciseId;
    private BigDecimal durationMinutes;
    private Integer sets;
    private Integer reps;
    private BigDecimal weightKg;
}