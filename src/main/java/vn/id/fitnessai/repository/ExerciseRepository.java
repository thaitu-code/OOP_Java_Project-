package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.Exercise;
public interface ExerciseRepository
        extends JpaRepository<Exercise, Integer> {
}
