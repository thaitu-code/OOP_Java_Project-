package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.enums.WorkoutStatus;

public interface WorkoutRepository
        extends JpaRepository<Workout, Integer> {

    boolean existsByUser_IdAndStatus(Long userId, WorkoutStatus status);
}
