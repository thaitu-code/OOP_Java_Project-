package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.ExerciseCategory;

public interface ExerciseCategoryRepository
        extends JpaRepository<ExerciseCategory,Integer> {

    boolean existsByCategoryName(String categoryName);
}
