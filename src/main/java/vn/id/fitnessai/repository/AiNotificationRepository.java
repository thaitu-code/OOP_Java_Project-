package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.AiNotification;
import java.util.*;
public interface AiNotificationRepository extends JpaRepository<AiNotification, Long> {
    Optional<AiNotification> findByUser_IdAndEventKey(Long userId, String eventKey);
    List<AiNotification> findTop100ByUser_IdOrderByIdDesc(Long userId);
}
