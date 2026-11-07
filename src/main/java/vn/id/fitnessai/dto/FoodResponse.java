package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import vn.id.fitnessai.entity.Food;
import vn.id.fitnessai.entity.FoodCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class FoodResponse {

    private Integer foodId;
    private String foodName;
    private String description;
    private String source;
    private String imageUrl;

    private BigDecimal caloriesPer100g;
    private BigDecimal proteinPer100g;
    private BigDecimal carbsPer100g;
    private BigDecimal fatPer100g;

    private boolean active;

    private Integer categoryId;
    private String categoryName;
    private boolean categoryActive;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static FoodResponse from(Food food) {
        FoodCategory category = food.getCategory();

        return new FoodResponse(
                food.getFoodId(),
                food.getFoodName(),
                food.getDescription(),
                food.getSource(),
                food.getImageUrl(),
                food.getCaloriesPer100g(),
                food.getProteinPer100g(),
                food.getCarbsPer100g(),
                food.getFatPer100g(),
                food.isActive(),
                category.getCategoryId(),
                category.getCategoryName(),
                category.isActive(),
                food.getCreatedAt(),
                food.getUpdatedAt()
        );
    }
}