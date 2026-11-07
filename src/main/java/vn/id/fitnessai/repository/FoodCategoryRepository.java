package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.FoodCategory;

import java.util.List;

public interface FoodCategoryRepository
        extends JpaRepository<FoodCategory, Integer> {

    boolean existsByCategoryNameKey(String categoryNameKey);

    boolean existsByCategoryNameKeyAndCategoryIdNot(String categoryNameKey, Integer categoryId);

    List<FoodCategory> findAllByActiveTrueOrderByCategoryNameAsc();
}
