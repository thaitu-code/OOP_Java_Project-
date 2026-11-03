package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.ExerciseLevel;
import java.util.Optional;

public interface ExerciseLevelRepository extends JpaRepository<ExerciseLevel, Integer> {
    Optional<ExerciseLevel> findByIdAndExercise_ExerciseId(Integer id, Integer exerciseId);

    boolean existsByExercise_ExerciseIdAndNameIgnoreCase(Integer exerciseId, String name);

    boolean existsByExercise_ExerciseIdAndNameIgnoreCaseAndIdNot(Integer exerciseId, String name, Integer id);
}
