package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import vn.id.fitnessai.enums.BadgeMetricType;

@Getter
@Setter
public class BadgeCreateRequest {
    @NotBlank
    private String name;
    @NotBlank
    private String description;
    private String icon;
    @NotNull
    private BadgeMetricType metricType;
    @Positive
    private long thresholdValue;
    private boolean active = true;
}
