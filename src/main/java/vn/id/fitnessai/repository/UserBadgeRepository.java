package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.UserBadge;
import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {
    boolean existsByUser_IdAndBadge_Id(Long userId, Long badgeId);
    List<UserBadge> findByUser_IdOrderByAwardedAtDesc(Long userId);
}
