package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.math.BigDecimal;
import java.util.*;

public interface WorkoutDetailRepository
        extends JpaRepository<WorkoutDetail, Integer> {

    boolean existsByWorkout_User_IdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long userId, java.time.OffsetDateTime start, java.time.OffsetDateTime end);

    //Check ID cùa bài tập cần xóa có liên kết với trạng thái của WorkoutDetail không
    boolean existsByExercise_ExerciseIdAndWorkout_Status(Integer ExerciseId, WorkoutStatus status);

    // Caller must lock Exercise and reject references from open workouts first.
    // Only detach the master-data link; snapshots and training results stay intact.
    @Modifying(flushAutomatically = true)
    @Query("update WorkoutDetail d set d.exercise = null where d.exercise.exerciseId = :exerciseId")
    int detachExerciseReferences(@Param("exerciseId") Integer exerciseId);
    @Query("""
    SELECT COALESCE(SUM(d.caloriesBurned), 0)
    FROM WorkoutDetail d
    WHERE d.workout.user.id = :userId
         AND d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.COMPLETED
    """)
    BigDecimal sumCaloriesByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT COALESCE(SUM(d.caloriesBurned), 0) FROM WorkoutDetail d
        WHERE d.workout.user.id = :userId
          AND d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.COMPLETED
          AND d.completedAt >= :start AND d.completedAt < :end
        """)
    BigDecimal sumCaloriesForDay(@Param("userId") Long userId,
            @Param("start") java.time.OffsetDateTime start, @Param("end") java.time.OffsetDateTime end);

    @Query("""
    SELECT MAX(d.weightKg)
    FROM WorkoutDetail d
    WHERE d.workout.user.id = :userId
         AND d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.COMPLETED
    """)
    Optional<BigDecimal> findMaxWeightByUserId(@Param("userId") Long userId);

    boolean existsByWorkout_WorkoutIdAndExercise_ExerciseId(Integer workoutId, Integer exerciseId);

    List<WorkoutDetail> findAllByWorkout_WorkoutIdOrderByDetailIdAsc(Integer workoutId);

    Optional<WorkoutDetail> findByDetailIdAndWorkout_workoutId(Integer detailId, Integer workoutId);

    boolean existsByWorkout_User_IdAndWorkout_StatusAndStatusIn(Long userId, WorkoutStatus workoutStatus, Collection<WorkoutDetailStatus> detailStatuses);

    boolean existsByWorkout_WorkoutIdAndStatus(Integer workoutId, WorkoutDetailStatus status);

    interface WorkoutTotals {
        Integer getWorkoutId();
        BigDecimal getDurationMinutes();
        BigDecimal getCaloriesBurned();
    }

    @Query("""
            SELECT d.workout.workoutId AS workoutId,
                   SUM(d.durationMinutes) AS durationMinutes,
                   SUM(d.caloriesBurned) AS caloriesBurned
            FROM WorkoutDetail d
            WHERE d.workout.workoutId IN :workoutIds
              AND d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.COMPLETED
            GROUP BY d.workout.workoutId
            """)
    List<WorkoutTotals> sumCompletedByWorkoutIds(@Param("workoutIds") Collection<Integer> workoutIds);

    @Query("""
            SELECT DISTINCT d.workout.workoutId FROM WorkoutDetail d
            WHERE d.workout.status = vn.id.fitnessai.enums.WorkoutStatus.IN_PROGRESS
              AND (d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.IN_PROGRESS
                   OR (d.status = vn.id.fitnessai.enums.WorkoutDetailStatus.RESTING AND d.estimatedSecondsSnapshot IS NOT NULL))
              AND d.lastHeartbeatAt < :cutoff
            ORDER BY d.workout.workoutId
            """)
    List<Integer> findExpiredWorkoutIds(@Param("cutoff") java.time.OffsetDateTime cutoff, Pageable pageable);
}
