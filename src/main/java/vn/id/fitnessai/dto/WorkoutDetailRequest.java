package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
}
