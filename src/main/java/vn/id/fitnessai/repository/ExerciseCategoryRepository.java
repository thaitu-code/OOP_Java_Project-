package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.ExerciseCategory;

public interface ExerciseCategoryRepository
        extends JpaRepository<ExerciseCategory,Integer> {

    //Check trùng tên (dùng khi create)
    boolean existsByCategoryName(String categoryName);

    //Check trùng tên (dùng khi update)
    boolean existsByCategoryNameAndCategoryIdNot(String categoryName, Integer categoryId);
}

