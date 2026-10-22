package vn.id.fitnessai.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.WorkoutResponse;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.WorkoutRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkoutRepository workoutRepository;
    private final UserRepository userRepository;

    @Transactional
    public WorkoutResponse createWorkout(String username){

        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }

        User user = userRepository.findByUsernameForUpdate(username).orElseThrow(() -> new IllegalArgumentException("Thông tin người dùng không được để trống"));

        if (!user.isEnabled() || user.isLocked()){
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        boolean hasActiveWorkout = workoutRepository.existsByUser_IdAndStatus(user.getId(), WorkoutStatus.IN_PROGRESS);

        if (hasActiveWorkout){
            throw new IllegalStateException("Bạn đang có một buổi tập chưa kết thúc");
        }

        Workout workout = new Workout(user, OffsetDateTime.now(VIETNAM_ZONE));

        Workout savedWorkout = workoutRepository.save(workout);

        return new WorkoutResponse(
                savedWorkout.getWorkoutId(),
                savedWorkout.getUser().getId(),
                savedWorkout.getStartTime(),
                savedWorkout.getEndTime(),
                savedWorkout.getStatus(),
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );
    }
}
