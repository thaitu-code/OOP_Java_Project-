package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.util.Optional;

public interface WorkoutRepository
        extends JpaRepository<Workout, Integer> {

    Optional<Workout> findFirstByUser_IdOrderByStartTimeAsc(Long userId);

    boolean existsByUser_IdAndStatus(Long userId, WorkoutStatus status);
    long countByUser_IdAndStatus(Long userId, WorkoutStatus status);

    Optional<Workout> findByUser_IdAndStatus(Long userId, WorkoutStatus status);

    Page<Workout> findByUser_Id(Long userId, Pageable pageable);

    Page<Workout> findByUser_IdAndStatus(Long userId, WorkoutStatus status, Pageable pageable);

    @Query("SELECT w.user.id FROM Workout w WHERE w.workoutId = :id")
    Optional<Long> findOwnerIdByWorkoutId(@Param("id") Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Workout w WHERE w.workoutId = :id")
    Optional<Workout> findByIdForUpdate(@Param("id") Integer id);
}
