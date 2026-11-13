package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.AiChatTurn;
import java.time.OffsetDateTime;
import java.util.*;
public interface AiChatTurnRepository extends JpaRepository<AiChatTurn, UUID> {
    Optional<AiChatTurn> findByUser_IdAndRequestId(Long userId, UUID requestId);
    boolean existsByUser_IdAndStatusAndCreatedAtAfter(Long userId, String status, OffsetDateTime cutoff);
    long countByUser_IdAndCreatedAtAfter(Long userId, OffsetDateTime cutoff);
}
