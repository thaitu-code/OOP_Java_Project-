package vn.id.fitnessai.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import vn.id.fitnessai.enums.ActivityLevel;
import vn.id.fitnessai.enums.FitnessGoal;
import vn.id.fitnessai.enums.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class ProfileUpdateRequest {

    @NotBlank(message = "Tên hiển thị không được để trống")
    private String displayName;

    @NotNull(message = "Vui lòng chọn giới tính")
    private Gender gender;

    @NotNull(message = "Vui lòng nhập ngày sinh")
    @Past(message = "Ngày sinh phải trước ngày hiện tại")
    private LocalDate birthDate;

    @NotNull(message = "Chiều cao không được để trống")
    @DecimalMin(value = "50.0", message = "Chiều cao tối thiểu là 50cm")
    @DecimalMax(value = "260.0", message = "Chiều cao tối đa là 260cm")
    private BigDecimal heightCm;

    @NotNull(message = "Cân nặng cơ thể không được để trống")
    @DecimalMin(value = "20.0", message = "Cân nặng tối thiểu là 20kg")
    @DecimalMax(value = "400.0", message = "Cân nặng tối đa là 400kg")
    private BigDecimal weightKg;

    @NotNull(message = "Vui lòng chọn mức độ vận động")
    private ActivityLevel activityLevel;

    @NotNull(message = "Vui lòng chọn mục tiêu tập luyện")
    private FitnessGoal goal;
}