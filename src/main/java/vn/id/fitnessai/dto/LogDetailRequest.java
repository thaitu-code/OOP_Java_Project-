package vn.id.fitnessai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.id.fitnessai.enums.MealType;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class LogDetailRequest {

    @NotNull(message = "Thực phẩm không được để trống")
    @Positive(message = "ID thực phẩm phải là số nguyên dương")
    private Integer foodId;

    @NotNull(message = "Bữa ăn không được để trống")
    private MealType mealType;

    @NotNull(message = "Khối lượng không được để trống")
    @DecimalMin(
            value = "0",
            inclusive = false,
            message = "Khối lượng phải lớn hơn 0"
    )
    @Digits(
            integer = 5,
            fraction = 2,
            message = "Khối lượng tối đa 5 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal weightGrams;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}