package vn.id.fitnessai.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.id.fitnessai.entity.NutritionLog;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

public interface NutritionLogRepository
        extends JpaRepository<NutritionLog, Integer> {

    Optional<NutritionLog> findByUser_IdAndLogDate(
            Long userId,
            LocalDate logDate
    );

    Page<NutritionLog> findByUser_IdOrderByLogDateDesc(
            Long userId,
            Pageable pageable
    );

    @Modifying
    @Query(value = """
            INSERT INTO daily_meal_logs (user_id, log_date, created_at)
            VALUES (:userId, :logDate, :createdAt)
            ON CONFLICT (user_id, log_date) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("userId") Long userId,
            @Param("logDate") LocalDate logDate,
            @Param("createdAt") OffsetDateTime createdAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT l FROM NutritionLog l
            WHERE l.user.id = :userId
              AND l.logDate = :logDate
            """)
    Optional<NutritionLog> findByUserIdAndLogDateForUpdate(
            @Param("userId") Long userId,
            @Param("logDate") LocalDate logDate
    );
}