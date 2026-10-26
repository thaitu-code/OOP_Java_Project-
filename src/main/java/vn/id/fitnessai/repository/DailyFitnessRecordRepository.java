package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.DailyFitnessRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyFitnessRecordRepository extends JpaRepository<DailyFitnessRecord, Long> {
    Optional<DailyFitnessRecord> findByUser_IdAndActivityDate(Long userId, LocalDate activityDate);
    List<DailyFitnessRecord> findByUser_IdAndActivityDateBetweenOrderByActivityDateAsc(Long userId, LocalDate start, LocalDate end);
    List<DailyFitnessRecord> findByActivityDateLessThanEqualAndFinalizedFalseOrderByActivityDateAsc(LocalDate date);
}
