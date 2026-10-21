package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.Exercise;

import java.util.Optional;

public interface ExerciseRepository
        extends JpaRepository<Exercise, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Exercise e where e.exerciseId = :exerciseId")
    Optional<Exercise> findByIdForUpdate(
            @Param("exerciseId") Integer exerciseId);
}
