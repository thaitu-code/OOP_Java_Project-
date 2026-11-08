package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.AiRecommendedExercise;
import java.util.List;
public interface AiRecommendedExerciseRepository extends JpaRepository<AiRecommendedExercise,Long> {
 List<AiRecommendedExercise> findByRecommendation_IdOrderByDayOfWeekAscPositionAsc(Long id);
}
