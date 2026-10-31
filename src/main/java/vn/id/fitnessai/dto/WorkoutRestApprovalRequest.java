package vn.id.fitnessai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkoutRestApprovalRequest {
    @NotNull(message = "Thời lượng nghỉ không được để trống")
    @Min(value = 1, message = "Thời lượng nghỉ phải từ 1 giây trở lên")
    @Max(value = 300, message = "Thời lượng nghỉ không được vượt quá 300 giây")
    private Integer restSeconds;
}
