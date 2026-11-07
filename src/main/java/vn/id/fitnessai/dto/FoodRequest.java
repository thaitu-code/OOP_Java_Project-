package vn.id.fitnessai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class FoodRequest {

    @NotNull(message = "Nhóm thực phẩm không được để trống")
    @Positive(message = "ID nhóm thực phẩm phải là số nguyên dương")
    private Integer categoryId;

    @NotBlank(message = "Tên thực phẩm không được để trống")
    @Size(max = 150, message = "Tên thực phẩm không được vượt quá 150 ký tự")
    private String foodName;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    private String description;

    @Size(max = 500, message = "Nguồn thông tin không được vượt quá 500 ký tự")
    private String source;

    @Size(max = 500, message = "URL ảnh không được vượt quá 500 ký tự")
    private String imageUrl;

    @NotNull(message = "Calo không được để trống")
    @DecimalMin(value = "0", message = "Calo phải lớn hơn hoặc bằng 0")
    @Digits(
            integer = 5,
            fraction = 2,
            message = "Calo tối đa 5 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal caloriesPer100g;

    @NotNull(message = "Đạm không được để trống")
    @DecimalMin(value = "0", message = "Đạm phải lớn hơn hoặc bằng 0")
    @Digits(
            integer = 4,
            fraction = 2,
            message = "Đạm tối đa 4 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal proteinPer100g;

    @NotNull(message = "Tinh bột không được để trống")
    @DecimalMin(value = "0", message = "Tinh bột phải lớn hơn hoặc bằng 0")
    @Digits(
            integer = 4,
            fraction = 2,
            message = "Tinh bột tối đa 4 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal carbsPer100g;

    @NotNull(message = "Chất béo không được để trống")
    @DecimalMin(value = "0", message = "Chất béo phải lớn hơn hoặc bằng 0")
    @Digits(
            integer = 4,
            fraction = 2,
            message = "Chất béo tối đa 4 chữ số nguyên và 2 chữ số thập phân"
    )
    private BigDecimal fatPer100g;
}