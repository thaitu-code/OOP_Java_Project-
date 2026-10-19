package vn.id.fitnessai.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ExerciseRequest {

    @NotBlank(message = "Tên bài tập không được để trống")
    @Size(max = 150, message = "Tên bài tập không được vượt quá 150 kí tự")
    private String exerciseName;

    private String description;

    @NotNull(message = "MET không được để trống")
    @Positive(message = "MET phải lớn hơn 0")
    @Digits(integer = 3, fraction = 1, message = "MET tối đa 3 chữ số nguyên và 1 chữ số thập phân")
    private BigDecimal metValue;

    @NotNull(message = "Thời gian không được để trống")
    @Positive(message = "Thời gian phải lớn hơn 0")
    @Digits(integer = 5, fraction = 1, message = "Thời gian tối đa 5 chữ số phần nguyên và 1 chữ số thập phân")
    private BigDecimal minDurationMinutes;

    @Positive(message = "Số hiệp đề xuất phải lớn hơn 0")
    private Integer recommendedSets;

    @Positive(message = "Số lần mỗi hiệp đề xuất phải lớn hơn 0")
    private Integer recommendedReps;

    @Size(max = 500, message = "URL video không được vượt quá 500 kí tự")
    private String aiVideoUrl;

    @NotNull(message = "Nhóm bài tập không được để trống")
    @Positive(message = "ID nhóm bài tập phải là số nguyên dương")
    private Integer categoryId;
}
