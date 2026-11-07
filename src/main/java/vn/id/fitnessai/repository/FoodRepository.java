package vn.id.fitnessai.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.Food;

import java.util.Optional;

public interface FoodRepository
        extends JpaRepository<Food, Integer> {

    boolean existsByFoodNameKey(String foodNameKey);
    boolean existsByFoodNameKeyAndFoodIdNot(String foodNameKey, Integer foodId);

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Food> findById(Integer foodId);

    @EntityGraph(attributePaths = "category")
    Page<Food> findByFoodNameContainingIgnoreCase(String keyword, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Page<Food> findByActiveTrueAndCategory_ActiveTrueAndFoodNameContainingIgnoreCase(
            String keyword, Pageable pageable
    );

    @EntityGraph(attributePaths = "category")
    Page<Food> findByCategory_CategoryIdAndActiveTrueAndCategory_ActiveTrueAndFoodNameContainingIgnoreCase(
            Integer categoryId,
            String keyword,
            Pageable pageable
    );
}