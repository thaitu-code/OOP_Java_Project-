package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.WorkoutDetail;

public interface WorkoutDetailRepository
        extends JpaRepository<WorkoutDetail, Integer> {
}
