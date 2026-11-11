package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.LogDetail;
import vn.id.fitnessai.enums.MealType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface LogDetailRepository
        extends JpaRepository<LogDetail, Integer> {

    boolean existsByAiMealPlanItemId(Long aiMealPlanItemId);

    List<LogDetail> findAllByNutritionLog_LogIdAndNutritionLog_User_IdOrderByDetailIdAsc(
            Integer logId,
            Long userId
    );

    Optional<LogDetail> findByDetailIdAndNutritionLog_LogIdAndNutritionLog_User_Id(
            Integer detailId,
            Integer logId,
            Long userId
    );

    interface NutritionTotals{
        BigDecimal getCalories();
        BigDecimal getProtein();
        BigDecimal getCarbs();
        BigDecimal getFat();
    }

    interface MealTotals extends NutritionTotals{
        MealType getMealType();
    }

    @Query("""
            SELECT COALESCE(SUM(d.calculatedCalories), 0) AS calories,
                   COALESCE(SUM(d.calculatedProtein), 0) AS protein,
                   COALESCE(SUM(d.calculatedCarbs), 0) AS carbs,
                   COALESCE(SUM(d.calculatedFat), 0) AS fat
            FROM LogDetail d
            WHERE d.nutritionLog.logId = :logId
              AND d.nutritionLog.user.id = :userId
            """)
    NutritionTotals sumByLogIdAndUserId(
            @Param("logId") Integer logId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT d.mealType AS mealType,
                   SUM(d.calculatedCalories) AS calories,
                   SUM(d.calculatedProtein) AS protein,
                   SUM(d.calculatedCarbs) AS carbs,
                   SUM(d.calculatedFat) AS fat
            FROM LogDetail d
            WHERE d.nutritionLog.logId = :logId
              AND d.nutritionLog.user.id = :userId
            GROUP BY d.mealType
            """)
    List<MealTotals> sumByMealTypeForLog(
            @Param("logId") Integer logId,
            @Param("userId") Long userId
    );

    @Query("""
            SELECT COUNT(DISTINCT d.nutritionLog.logDate)
            FROM LogDetail d
            WHERE d.nutritionLog.user.id = :userId
            """)
    long countLoggedDaysByUserId(@Param("userId") Long userId);


    interface LogHistoryTotals extends NutritionTotals {
        Integer getLogId();
        Long getItemCount();
    }

    @Query("""
        SELECT d.nutritionLog.logId AS logId,
               COUNT(d.detailId) AS itemCount,
               SUM(d.calculatedCalories) AS calories,
               SUM(d.calculatedProtein) AS protein,
               SUM(d.calculatedCarbs) AS carbs,
               SUM(d.calculatedFat) AS fat
        FROM LogDetail d
        WHERE d.nutritionLog.user.id = :userId
          AND d.nutritionLog.logId IN (:logIds)
        GROUP BY d.nutritionLog.logId
        """)
    List<LogHistoryTotals> sumForHistory(
            @Param("logIds") List<Integer> logIds,
            @Param("userId") Long userId
    );
}
