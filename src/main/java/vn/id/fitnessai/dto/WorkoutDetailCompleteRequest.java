package vn.id.fitnessai.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class WorkoutDetailCompleteRequest {

    @NotNull(message = "Token timer không được để trống")
    private UUID timerToken;

    @PositiveOrZero(message = "Số hiệp không được âm")
    private Integer sets;

    @PositiveOrZero(message = "Số lần mỗi hiệp không được âm")
    private Integer reps;

    @PositiveOrZero(message = "Tải tập không được âm")
    @Digits(
            integer = 4,
            fraction = 2,
            message = "Tải tập tối đa 4 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal weightKg;
}