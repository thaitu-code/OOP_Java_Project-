package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class NutritionTotalsResponse {

    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;

    public static NutritionTotalsResponse zero() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);

        return new NutritionTotalsResponse(
                zero,
                zero,
                zero,
                zero
        );
    }
}