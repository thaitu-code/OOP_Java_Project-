package vn.id.fitnessai.dto;

import lombok.Builder;
import lombok.Getter;
import vn.id.fitnessai.enums.ActivityLevel;
import vn.id.fitnessai.enums.FitnessGoal;
import vn.id.fitnessai.enums.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class UserProfileResponse {
    private String username;
    private String displayName;
    private Gender gender;
    private LocalDate birthDate;
    private Integer age;
    private BigDecimal heightCm;
    private BigDecimal weightKg;
    private ActivityLevel activityLevel;
    private FitnessGoal goal;
    private BigDecimal calculatedBmr;
    private BigDecimal calculatedTdee;
    private LocalDateTime profileCalculatedAt;
}