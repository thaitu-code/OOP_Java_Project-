package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.AiActionDraft;
import java.util.*;
public interface AiActionDraftRepository extends JpaRepository<AiActionDraft, UUID> {
    List<AiActionDraft> findByUser_IdAndSessionIdAndStatusOrderByCreatedAtAsc(Long userId, String sessionId, String status);
    List<AiActionDraft> findByUser_IdAndTurnIdOrderByCreatedAtAsc(Long userId, UUID turnId);
}
