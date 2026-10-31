package vn.id.fitnessai.dto;

import vn.id.fitnessai.entity.ExerciseLevel;

public record ExerciseLevelResponse(Integer id, String name, int sets, Integer reps, int secondsPerSet,
                                    int restSeconds, int estimatedSeconds, boolean active) {
    public static ExerciseLevelResponse from(ExerciseLevel level) {
        return new ExerciseLevelResponse(level.getId(), level.getName(), level.getSets(), level.getReps(),
                level.getSecondsPerSet(), level.getRestSeconds(), level.getEstimatedSeconds(), level.isActive());
    }
}
