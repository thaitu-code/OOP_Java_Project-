package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.id.fitnessai.enums.MealType;
import vn.id.fitnessai.enums.NutritionEntrySource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

@Entity
@Table(name = "meal_details", indexes =
        @Index(name = "ix_meal_detail_log_meal", columnList = "meal_log_id,meal_type"), check =
        @CheckConstraint(name = "ck_meal_detail_values", constraint =
        "weight_grams > 0 AND calculated_calories >= 0 AND calculated_protein >= 0"
        + " AND calculated_carbs >= 0 AND calculated_fat >= 0"
        + " AND calories_per_100g_snapshot >= 0 AND protein_per_100g_snapshot >= 0"
        + " AND carbs_per_100g_snapshot >= 0 AND fat_per_100g_snapshot >= 0 AND updated_at >= logged_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_detail_id")
    private Integer detailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_log_id", nullable = false, updatable = false)
    private NutritionLog nutritionLog;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false)
    private Food food;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 16)
    private MealType mealType;

    @Column(name = "weight_grams", nullable = false, precision = 7, scale = 2)
    private BigDecimal weightGrams;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "food_name_snapshot", nullable = false, length = 150)
    private String foodNameSnapshot;

    @Column(name = "category_name_snapshot", nullable = false, length = 100)
    private String categoryNameSnapshot;

    @Column(name = "image_url_snapshot", length = 500)
    private String imageUrlSnapshot;

    @Column(name = "calories_per_100g_snapshot", nullable = false, precision = 7, scale = 2)
    private BigDecimal caloriesPer100gSnapshot;

    @Column(name = "protein_per_100g_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal proteinPer100gSnapshot;

    @Column(name = "carbs_per_100g_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal carbsPer100gSnapshot;

    @Column(name = "fat_per_100g_snapshot", nullable = false, precision = 6, scale = 2)
    private BigDecimal fatPer100gSnapshot;

    @Column(name = "calculated_calories", nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedCalories;

    @Column(name = "calculated_protein", nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedProtein;

    @Column(name = "calculated_carbs", nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedCarbs;

    @Column(name = "calculated_fat", nullable = false, precision = 12, scale = 2)
    private BigDecimal calculatedFat;

    @Column(name = "logged_at", nullable = false, updatable = false)
    private OffsetDateTime loggedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_source", nullable = false, updatable = false, length = 16)
    private NutritionEntrySource createdSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_modified_source", nullable = false, length = 16)
    private NutritionEntrySource lastModifiedSource;

    // One AI plan item can be logged at most once, even if requests are retried.
    @Column(name = "ai_meal_plan_item_id", unique = true)
    private Long aiMealPlanItemId;

    public void linkAiMealPlanItem(Long itemId) {
        if (itemId == null || itemId <= 0 || aiMealPlanItemId != null || createdSource != NutritionEntrySource.AI)
            throw new IllegalArgumentException("Mã món trong lịch AI không hợp lệ");
        aiMealPlanItemId = itemId;
    }

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public LogDetail(NutritionLog log, Food food, MealType mealType, BigDecimal grams,
                     String note, NutritionEntrySource source, OffsetDateTime now) {

        if (log == null) throw new IllegalArgumentException("Món ăn phải thuộc một nhật ký");
        this.nutritionLog = log;

        update(food, mealType, grams, note, source, now);
        this.loggedAt = this.updatedAt;
        this.createdSource = this.lastModifiedSource;
    }

    /** Call only after the service has checked ownership, account state, and expected version. */
    public void update(Food selectedFood, MealType mealType, BigDecimal grams, String note,
                       NutritionEntrySource source, OffsetDateTime now) {

        nutritionLog.requireEditableAt(now);
        if (selectedFood == null || mealType == null || source == null) {
            throw new IllegalArgumentException("Thiếu thực phẩm, bữa ăn hoặc nguồn thao tác");
        }

        OffsetDateTime timestamp = NutritionValidation.time(now, updatedAt);
        BigDecimal validGrams = NutritionValidation.number(grams, 7, true);
        String validNote = NutritionValidation.optionalText(note, 500);

        boolean sameFood = food == selectedFood || (food != null && food.getFoodId() != null
                && food.getFoodId().equals(selectedFood.getFoodId()));

        if (!sameFood && !selectedFood.isAvailableForLogging()) {
            throw new IllegalStateException("Thực phẩm hoặc nhóm đã ngừng hoạt động");
        }

        Snapshot snapshot = sameFood ? currentSnapshot() : Snapshot.from(selectedFood);
        BigDecimal calories = calculate(snapshot.calories(), validGrams);
        BigDecimal protein = calculate(snapshot.protein(), validGrams);
        BigDecimal carbs = calculate(snapshot.carbs(), validGrams);
        BigDecimal fat = calculate(snapshot.fat(), validGrams);

        this.food = selectedFood;
        this.mealType = mealType;
        this.weightGrams = validGrams;
        this.note = validNote;
        this.foodNameSnapshot = snapshot.name();
        this.categoryNameSnapshot = snapshot.category();
        this.imageUrlSnapshot = snapshot.image();
        this.caloriesPer100gSnapshot = snapshot.calories();
        this.proteinPer100gSnapshot = snapshot.protein();
        this.carbsPer100gSnapshot = snapshot.carbs();
        this.fatPer100gSnapshot = snapshot.fat();
        this.calculatedCalories = calories;
        this.calculatedProtein = protein;
        this.calculatedCarbs = carbs;
        this.calculatedFat = fat;
        this.updatedAt = timestamp;
        this.lastModifiedSource = source;
    }

    private static BigDecimal calculate(BigDecimal per100g, BigDecimal grams) {
        return NutritionValidation.number(per100g.multiply(grams).movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP), 12, false);
    }

    private Snapshot currentSnapshot() {
        return new Snapshot(foodNameSnapshot, categoryNameSnapshot, imageUrlSnapshot,
                caloriesPer100gSnapshot, proteinPer100gSnapshot, carbsPer100gSnapshot, fatPer100gSnapshot);
    }

    private record Snapshot(String name, String category, String image, BigDecimal calories,
                            BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        static Snapshot from(Food food) {
            return new Snapshot(food.getFoodName(), food.getCategory().getCategoryName(), food.getImageUrl(),
                    food.getCaloriesPer100g(), food.getProteinPer100g(), food.getCarbsPer100g(), food.getFatPer100g());
        }
    }
}
