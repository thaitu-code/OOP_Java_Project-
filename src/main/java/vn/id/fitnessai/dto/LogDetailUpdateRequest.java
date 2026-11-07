package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LogDetailUpdateRequest extends LogDetailRequest {

    @NotNull(message = "Version không được để trống")
    @PositiveOrZero(message = "Version phải lớn hơn hoặc bằng 0")
    private Long expectedVersion;
}