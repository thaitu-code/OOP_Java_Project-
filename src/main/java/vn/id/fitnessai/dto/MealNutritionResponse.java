package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import vn.id.fitnessai.enums.MealType;

import java.util.List;

@Getter
@AllArgsConstructor
public class MealNutritionResponse {

    private MealType mealType;
    private List<LogDetailResponse> items;
    private NutritionTotalsResponse totals;
}