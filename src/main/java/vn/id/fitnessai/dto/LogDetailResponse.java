package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import vn.id.fitnessai.entity.LogDetail;
import vn.id.fitnessai.enums.MealType;
import vn.id.fitnessai.enums.NutritionEntrySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class LogDetailResponse {

    private Integer detailId;
    private Integer logId;
    private Integer foodId;

    private MealType mealType;
    private BigDecimal weightGrams;
    private String note;

    private String foodNameSnapshot;
    private String categoryNameSnapshot;
    private String imageUrlSnapshot;

    private BigDecimal caloriesPer100gSnapshot;
    private BigDecimal proteinPer100gSnapshot;
    private BigDecimal carbsPer100gSnapshot;
    private BigDecimal fatPer100gSnapshot;

    private BigDecimal calculatedCalories;
    private BigDecimal calculatedProtein;
    private BigDecimal calculatedCarbs;
    private BigDecimal calculatedFat;

    private OffsetDateTime loggedAt;
    private OffsetDateTime updatedAt;

    private NutritionEntrySource createdSource;
    private NutritionEntrySource lastModifiedSource;

    private Long version;

    public static LogDetailResponse from(LogDetail detail) {
        return new LogDetailResponse(
                detail.getDetailId(),
                detail.getNutritionLog().getLogId(),
                detail.getFood().getFoodId(),
                detail.getMealType(),
                detail.getWeightGrams(),
                detail.getNote(),
                detail.getFoodNameSnapshot(),
                detail.getCategoryNameSnapshot(),
                detail.getImageUrlSnapshot(),
                detail.getCaloriesPer100gSnapshot(),
                detail.getProteinPer100gSnapshot(),
                detail.getCarbsPer100gSnapshot(),
                detail.getFatPer100gSnapshot(),
                detail.getCalculatedCalories(),
                detail.getCalculatedProtein(),
                detail.getCalculatedCarbs(),
                detail.getCalculatedFat(),
                detail.getLoggedAt(),
                detail.getUpdatedAt(),
                detail.getCreatedSource(),
                detail.getLastModifiedSource(),
                detail.getVersion()
        );
    }
}