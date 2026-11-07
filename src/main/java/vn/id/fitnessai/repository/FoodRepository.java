package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT f FROM Food f
        WHERE f.foodId = :foodId
        """)
    Optional<Food> findByIdForUpdate(
            @Param("foodId") Integer foodId
    );

    @Query("""
        SELECT f.category.categoryId
        FROM Food f
        WHERE f.foodId = :foodId
        """)
    Optional<Integer> findCategoryIdByFoodId(
            @Param("foodId") Integer foodId
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT f FROM Food f
        WHERE f.foodId = :foodId
        """)
    Optional<Food> findByIdForLogging(
            @Param("foodId") Integer foodId
    );
}