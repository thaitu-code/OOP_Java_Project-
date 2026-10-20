package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutStatus;

public interface WorkoutDetailRepository
        extends JpaRepository<WorkoutDetail, Integer> {

    //Check ID cùa bài tập cần xóa có liên kết với trạng thái của WorkoutDetail không
    boolean existsByExercise_ExerciseIdAndWorkout_Status(Integer ExerciseId, WorkoutStatus status);
}
