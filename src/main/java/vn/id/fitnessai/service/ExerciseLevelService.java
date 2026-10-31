package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;

@Service
@RequiredArgsConstructor
public class ExerciseLevelService {
    private final ExerciseRepository exercises;
    private final ExerciseLevelRepository levels;

    @Transactional
    public ExerciseLevelResponse save(Integer exerciseId, Integer levelId, ExerciseLevelRequest request) {
        Exercise exercise = exercises.findByIdForUpdate(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        String name = request.getName().strip();

        boolean duplicate = levelId == null
                ? levels.existsByExercise_ExerciseIdAndNameIgnoreCase(exerciseId, name)
                : levels.existsByExercise_ExerciseIdAndNameIgnoreCaseAndIdNot(exerciseId, name, levelId);

        if (duplicate) throw new IllegalArgumentException("Tên cấp độ đã tồn tại trong bài tập này");

        ExerciseLevel level;

        if (levelId == null) {
            level = new ExerciseLevel(exercise, name, request.getSets(), request.getReps(),
                    request.getSecondsPerSet(), request.getRestSeconds(), request.getEstimatedSeconds());
        } else {
            level = levels.findByIdAndExercise_ExerciseId(levelId, exerciseId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

            level.configure(name, request.getSets(), request.getReps(), request.getSecondsPerSet(),
                    request.getRestSeconds(), request.getEstimatedSeconds());
        }

        return ExerciseLevelResponse.from(levels.saveAndFlush(level));
    }

    @Transactional
    public ExerciseLevelResponse status(Integer exerciseId, Integer levelId, boolean active) {
        exercises.findByIdForUpdate(exerciseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ExerciseLevel level = levels.findByIdAndExercise_ExerciseId(levelId, exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        level.setActive(active);

        return ExerciseLevelResponse.from(levels.saveAndFlush(level));
    }
}
