package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.math.BigDecimal;
import java.util.*;

public interface WorkoutDetailRepository
        extends JpaRepository<WorkoutDetail, Integer> {

    //Check ID cùa bài tập cần xóa có liên kết với trạng thái của WorkoutDetail không
    boolean existsByExercise_ExerciseIdAndWorkout_Status(Integer ExerciseId, WorkoutStatus status);
    @Query("""
    SELECT COALESCE(SUM(d.caloriesBurned), 0)
    FROM WorkoutDetail d
    WHERE d.workout.user.id = :userId
    """)
    BigDecimal sumCaloriesByUserId(@Param("userId") Long userId);

    @Query("""
    SELECT MAX(d.weightKg)
    FROM WorkoutDetail d
    WHERE d.workout.user.id = :userId
    """)
    Optional<BigDecimal> findMaxWeightByUserId(@Param("userId") Long userId);

    boolean existsByWorkout_WorkoutIdAndExercise_ExerciseId(Integer workoutId, Integer exerciseId);

    List<WorkoutDetail> findAllByWorkout_WorkoutIdOrderByDetailIdAsc(Integer workoutId);
}
