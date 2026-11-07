package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class NutritionLogHistoryResponse {

    private Integer logId;
    private LocalDate logDate;
    private OffsetDateTime createdAt;

    private long itemCount;
    private NutritionTotalsResponse totals;
}