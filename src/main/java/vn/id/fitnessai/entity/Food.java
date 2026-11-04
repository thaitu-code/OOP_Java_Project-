package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "foods", uniqueConstraints =
        @UniqueConstraint(name = "uk_food_name_key", columnNames = "food_name_key"), indexes =
        @Index(name = "ix_food_category_active", columnList = "category_id,is_active"), check =
        @CheckConstraint(name = "ck_food_values", constraint =
                "calories_per_100g >= 0 AND protein >= 0 AND carbs >= 0 AND fat >= 0 AND updated_at >= created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "food_id")
    private Integer foodId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private FoodCategory category;

    @Column(name = "food_name", nullable = false, length = 150)
    private String foodName;

    @Column(name = "food_name_key", nullable = false, length = 150)
    private String foodNameKey;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "source", length = 500)
    private String source;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "calories_per_100g", nullable = false, precision = 7, scale = 2)
    private BigDecimal caloriesPer100g;

    @Column(name = "protein", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinPer100g;

    @Column(name = "carbs", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsPer100g;

    @Column(name = "fat", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatPer100g;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Food(FoodCategory category, String name, String description, String source, String imageUrl,
                BigDecimal calories, BigDecimal protein, BigDecimal carbs, BigDecimal fat, OffsetDateTime now) {

        update(category, name, description, source, imageUrl, calories, protein, carbs, fat, now);
        this.createdAt = this.updatedAt;
    }

    public void update(FoodCategory category, String name, String description, String source, String imageUrl,
                       BigDecimal calories, BigDecimal protein, BigDecimal carbs, BigDecimal fat, OffsetDateTime now) {

        if (category == null) throw new IllegalArgumentException("Thực phẩm phải thuộc một nhóm");

        boolean sameCategory = this.category == category || (this.category != null
                && this.category.getCategoryId() != null && this.category.getCategoryId().equals(category.getCategoryId()));

        if (!sameCategory && !category.isActive()) throw new IllegalStateException("Không thể gán vào nhóm đã ngừng hoạt động");

        String validName = NutritionValidation.name(name, 150);
        String key = NutritionValidation.nameKey(validName);

        if (key.length() > 150) throw new IllegalArgumentException("Khóa tên thực phẩm vượt giới hạn lưu trữ");

        String validDescription = NutritionValidation.optionalText(description, 2000);
        String validSource = NutritionValidation.optionalText(source, 500);
        String validImage = NutritionValidation.imageUrl(imageUrl);

        BigDecimal validCalories = NutritionValidation.number(calories, 7, false);
        BigDecimal validProtein = NutritionValidation.number(protein, 6, false);
        BigDecimal validCarbs = NutritionValidation.number(carbs, 6, false);
        BigDecimal validFat = NutritionValidation.number(fat, 6, false);

        OffsetDateTime timestamp = NutritionValidation.time(now, updatedAt);

        this.category = category;
        this.foodName = validName;
        this.foodNameKey = key;
        this.description = validDescription;
        this.source = validSource;
        this.imageUrl = validImage;
        this.caloriesPer100g = validCalories;
        this.proteinPer100g = validProtein;
        this.carbsPer100g = validCarbs;
        this.fatPer100g = validFat;
        this.updatedAt = timestamp;
    }

    public boolean isAvailableForLogging() {
        return active && category != null && category.isActive();
    }

    public void setActive(boolean active, OffsetDateTime now) {
        OffsetDateTime timestamp = NutritionValidation.time(now, updatedAt);
        this.active = active;
        this.updatedAt = timestamp;
    }
}
