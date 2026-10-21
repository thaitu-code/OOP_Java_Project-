package vn.id.fitnessai.service;

import org.springframework.stereotype.Component;
import vn.id.fitnessai.enums.ActivityLevel;
import vn.id.fitnessai.enums.Gender;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

@Component
public class BodyMetricsCalculator {

    public static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tính tuổi tròn theo múi giờ Việt Nam.
     * Xử lý chính xác ngày sinh nhật và cả năm nhuận (29/02).
     */
    public int calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            throw new IllegalArgumentException("Ngày sinh không được để trống");
        }
        LocalDate todayVN = LocalDate.now(ZONE_VN);
        if (birthDate.isAfter(todayVN)) {
            throw new IllegalArgumentException("Ngày sinh không được lớn hơn ngày hiện tại");
        }
        return Period.between(birthDate, todayVN).getYears();
    }

    /**
     * BMR (Mifflin–St Jeor):
     * MALE:   10 × W + 6.25 × H - 5 × A + 5
     * FEMALE: 10 × W + 6.25 × H - 5 × A - 161
     */
    public BigDecimal calculateBmr(BigDecimal weightKg, BigDecimal heightCm, int ageYears, Gender gender) {
        if (weightKg == null || heightCm == null || gender == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu tính BMR");
        }

        BigDecimal wTerm = BigDecimal.valueOf(10).multiply(weightKg);
        BigDecimal hTerm = new BigDecimal("6.25").multiply(heightCm);
        BigDecimal aTerm = BigDecimal.valueOf(5).multiply(BigDecimal.valueOf(ageYears));

        BigDecimal genderAdjustment = switch (gender) {
            case MALE -> BigDecimal.valueOf(5);
            case FEMALE -> BigDecimal.valueOf(-161);
        };

        BigDecimal bmr = wTerm.add(hTerm).subtract(aTerm).add(genderAdjustment);
        return bmr.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * TDEE = BMR × hệ số vận động
     * SEDENTARY: 1.20 | LIGHT: 1.375 | MODERATE: 1.55 | ACTIVE: 1.725 | VERY_ACTIVE: 1.90
     */
    public BigDecimal calculateTdee(BigDecimal bmr, ActivityLevel activityLevel) {
        if (bmr == null || activityLevel == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu tính TDEE");
        }

        BigDecimal multiplier = switch (activityLevel) {
            case SEDENTARY -> new BigDecimal("1.20");
            case LIGHT -> new BigDecimal("1.375");
            case MODERATE -> new BigDecimal("1.55");
            case ACTIVE -> new BigDecimal("1.725");
            case VERY_ACTIVE -> new BigDecimal("1.90");
        };

        return bmr.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}