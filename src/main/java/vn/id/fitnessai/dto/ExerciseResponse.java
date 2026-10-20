package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ExerciseResponse {
    private Integer exerciseId;
    private String exerciseName;

    private boolean active;
    private String description;

    private BigDecimal metValue;
    private BigDecimal minDurationMinutes;

    private Integer recommendedSets;
    private Integer recommendedReps;

    private String aiVideoUrl;

    private Integer categoryId;
    private String categoryName;
}
