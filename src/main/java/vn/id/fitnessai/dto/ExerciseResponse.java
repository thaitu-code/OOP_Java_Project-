package vn.id.fitnessai.dto;

import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import vn.id.fitnessai.entity.Exercise;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class ExerciseResponse {
    private final Integer exerciseId;
    private final String exerciseName;

    private final boolean active;
    private final String description;

    private final BigDecimal metValue;
    private final BigDecimal minDurationMinutes;

    private final Integer recommendedSets;
    private final Integer recommendedReps;

    private final String aiVideoUrl;

    private final Integer categoryId;
    private final String categoryName;

    @Setter private java.util.List<ExerciseLevelResponse> levels = java.util.List.of();
    @Setter private String mediaUrl;

    public static ExerciseResponse from(Exercise exercise) {
        ExerciseResponse response = new ExerciseResponse(exercise.getExerciseId(), exercise.getExerciseName(), exercise.isActive(),
                exercise.getDescription(), exercise.getMetValue(), exercise.getMinDurationMinutes(), exercise.getRecommendedSets(),
                exercise.getRecommendedReps(), exercise.getAiVideoUrl(), exercise.getCategory().getCategoryId(), exercise.getCategory().getCategoryName());
        response.setLevels(exercise.getLevels().stream().map(ExerciseLevelResponse::from).toList());
        response.setMediaUrl(exercise.getMediaUrl());
        return response;
    }
}
