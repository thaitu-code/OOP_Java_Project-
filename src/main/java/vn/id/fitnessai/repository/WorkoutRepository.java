package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.util.Optional;

public interface WorkoutRepository
        extends JpaRepository<Workout, Integer> {

    boolean existsByUser_IdAndStatus(Long userId, WorkoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workout w where w.workoutId = :workoutId")
    Optional<Workout> findByIdForUpdate(@Param("workoutId") Integer workoutId);
}
