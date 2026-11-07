package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FoodCategoryRequest {
    @NotBlank(message = "Tên nhóm thực phẩm không được để trống")
    @Size(max = 100, message = "Tên nhóm thực phẩm không được vượt quá 100 kí tự")
    private String categoryName;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 kí tự")
    private String description;
}
