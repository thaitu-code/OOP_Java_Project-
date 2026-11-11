package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Max;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkoutDetailRequest {

    @NotNull(message = "ID bài tập không được để trống")
    @Positive(message = "ID bài tập phải lớn hơn 0")
    private Integer exerciseId;

    @Positive(message = "ID cấp độ phải lớn hơn 0")
    private Integer levelId;

    // Optional planned values supplied by an AI plan; separate from actual completed results.
    @Positive(message = "Số hiệp phải lớn hơn 0")
    @Max(value = 30, message = "Số hiệp không được vượt quá 30")
    private Integer plannedSets;

    @Positive(message = "Số lần tập phải lớn hơn 0")
    @Max(value = 200, message = "Số lần tập không được vượt quá 200")
    private Integer plannedReps;
}
