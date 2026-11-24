package vn.id.fitnessai.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.id.fitnessai.entity.WorkoutSelection;
import java.util.Optional;
import java.util.UUID;
public interface WorkoutSelectionRepository extends JpaRepository<WorkoutSelection, Long> {
    Optional<WorkoutSelection> findByUser_IdAndRequestId(Long userId, UUID requestId);
}
