package vn.id.fitnessai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record WorkoutSelectionRequest(@NotNull UUID requestId,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid WorkoutDetailRequest> items) {}
