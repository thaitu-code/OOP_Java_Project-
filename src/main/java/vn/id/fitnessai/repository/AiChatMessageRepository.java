package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import vn.id.fitnessai.entity.AiChatMessage;
import java.util.List;
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage,Long> {
    List<AiChatMessage> findByUser_IdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long userId,Long cursor,Pageable pageable);
    List<AiChatMessage> findByUser_IdAndSessionIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long userId,String session,Long cursor,Pageable pageable);
}
