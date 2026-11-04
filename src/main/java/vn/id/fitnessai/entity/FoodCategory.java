package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "food_categories", uniqueConstraints =
        @UniqueConstraint(name = "uk_food_category_name_key", columnNames = "category_name_key"), check =
        @CheckConstraint(name = "ck_food_category_time", constraint = "updated_at >= created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FoodCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "category_name", nullable = false, length = 100)
    private String categoryName;

    @Column(name = "category_name_key", nullable = false, length = 100)
    private String categoryNameKey;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public FoodCategory(String name, String description, OffsetDateTime now) {
        update(name, description, now);
        this.createdAt = this.updatedAt;
    }

    public void update(String name, String description, OffsetDateTime now) {
        String validName = NutritionValidation.name(name, 100);
        String key = NutritionValidation.nameKey(validName);

        if (key.length() > 100) throw new IllegalArgumentException("Khóa tên nhóm vượt giới hạn lưu trữ");

        String validDescription = NutritionValidation.optionalText(description, 1000);
        OffsetDateTime timestamp = NutritionValidation.time(now, updatedAt);

        this.categoryName = validName;
        this.categoryNameKey = key;
        this.description = validDescription;
        this.updatedAt = timestamp;
    }

    public void setActive(boolean active, OffsetDateTime now) {
        OffsetDateTime timestamp = NutritionValidation.time(now, updatedAt);
        this.active = active;
        this.updatedAt = timestamp;
    }
}
