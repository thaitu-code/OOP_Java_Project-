package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.AiRecommendation;
import java.util.List;
public interface AiRecommendationRepository extends JpaRepository<AiRecommendation,Long> {
    List<AiRecommendation> findByProfile_User_IdAndTypeOrderByIdDesc(Long userId, String type);
    List<AiRecommendation> findTop100ByProfile_User_IdOrderByIdDesc(Long userId);
    List<AiRecommendation> findByProfile_User_IdAndTypeAndStatus(Long userId, String type, String status);
}
