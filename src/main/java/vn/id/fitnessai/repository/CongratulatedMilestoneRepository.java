package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.CongratulatedMilestone;

public interface CongratulatedMilestoneRepository extends JpaRepository<CongratulatedMilestone, Long> {
    boolean existsByUser_IdAndMilestoneValue(Long userId, int milestoneValue);
}
