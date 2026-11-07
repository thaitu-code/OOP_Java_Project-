package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class FoodCategoryResponse {
    private Integer categoryId;
    private String categoryName;
    private String description;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
