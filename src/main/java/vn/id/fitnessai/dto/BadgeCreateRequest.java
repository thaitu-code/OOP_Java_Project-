package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BadgeCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    private String icon;
    @Positive
    private long thresholdValue;
    private boolean active = true;
}
