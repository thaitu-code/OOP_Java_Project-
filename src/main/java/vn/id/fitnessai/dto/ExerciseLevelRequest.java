package vn.id.fitnessai.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter @Setter @NoArgsConstructor
public class ExerciseLevelRequest {
    @NotBlank @Size(max = 100) private String name;
    @NotNull @Min(1) @Max(100) private Integer sets;
    @Positive private Integer reps;
    @NotNull @Min(1) @Max(86400) private Integer secondsPerSet;
    @NotNull @Min(1) @Max(300) private Integer restSeconds;
    @NotNull @Min(1) @Max(86400) private Integer estimatedSeconds;
}
