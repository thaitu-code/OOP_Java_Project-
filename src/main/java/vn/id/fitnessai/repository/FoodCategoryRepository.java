package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.FoodCategory;

import java.util.List;
import java.util.Optional;

public interface FoodCategoryRepository
        extends JpaRepository<FoodCategory, Integer> {

    boolean existsByCategoryNameKey(String categoryNameKey);

    boolean existsByCategoryNameKeyAndCategoryIdNot(String categoryNameKey, Integer categoryId);

    List<FoodCategory> findAllByActiveTrueOrderByCategoryNameAsc();


    //Tìm để cập nhật nên cần lock lại để tránh bị chỉnh sửa trước khi cập nhật
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM FoodCategory c
            WHERE c.categoryId = :categoryId
            """)
    Optional<FoodCategory> findByIdForUpdate(
            @Param("categoryId") Integer categoryId
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT c FROM FoodCategory c
        WHERE c.categoryId = :categoryId
        """)
    Optional<FoodCategory> findByIdForLogging(
            @Param("categoryId") Integer categoryId
    );
}
