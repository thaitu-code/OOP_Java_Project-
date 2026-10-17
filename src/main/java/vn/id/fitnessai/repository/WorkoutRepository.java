package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.Workout;

public interface WorkoutRepository
        extends JpaRepository<Workout, Integer> {
}
