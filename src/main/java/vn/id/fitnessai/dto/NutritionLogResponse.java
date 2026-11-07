package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class NutritionLogResponse {

    private Integer logId;
    private LocalDate logDate;
    private OffsetDateTime createdAt;

    private NutritionTotalsResponse totals;
    private List<MealNutritionResponse> meals;
}