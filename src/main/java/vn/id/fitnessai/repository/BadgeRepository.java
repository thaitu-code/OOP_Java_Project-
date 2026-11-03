package vn.id.fitnessai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.Badge;
import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, Long> {
    List<Badge> findByActiveTrueOrderByIdAsc();
    boolean existsByNameIgnoreCase(String name);
}
