package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.AiMealPlanItem;
import java.util.List;
public interface AiMealPlanItemRepository extends JpaRepository<AiMealPlanItem,Long> {
    List<AiMealPlanItem> findByRecommendation_IdOrderByPlanDateAscIdAsc(Long id);
}
