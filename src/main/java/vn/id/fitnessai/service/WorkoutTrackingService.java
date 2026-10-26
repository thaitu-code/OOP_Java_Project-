package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class WorkoutTrackingService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final WorkoutRepository workoutRepository;
    private final WorkoutDetailRepository workoutDetailRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final CheckInStreakService checkInStreakService;
    private final BadgeService badgeService;

    @Transactional
    public WorkoutDetail addCompletedExercise(String username, Integer workoutId, WorkoutDetailRequest request) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        if (!user.isEnabled() || user.isLocked()) {
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }
        Workout workout = workoutRepository.findByIdForUpdate(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi tập"));
        if (!workout.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền sửa buổi tập này");
        }
        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalStateException("Buổi tập không còn ở trạng thái đang tập");
        }
        Exercise exercise = exerciseRepository.findById(request.getExerciseId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài tập"));
        if (!exercise.isActive()) throw new IllegalArgumentException("Bài tập đã ngừng hoạt động");
        if (request.getDurationMinutes().compareTo(exercise.getMinDurationMinutes()) < 0) {
            throw new IllegalArgumentException("Thời lượng chưa đạt mức tối thiểu để công nhận hoàn thành bài tập");
        }
        if (user.getProfile() == null || user.getProfile().getWeightKg() == null
                || user.getProfile().getWeightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Hãy cập nhật cân nặng trong hồ sơ trước khi lưu kết quả bài tập");
        }

        BigDecimal calories = exercise.getMetValue()
                .multiply(new BigDecimal("3.5"))
                .multiply(user.getProfile().getWeightKg())
                .divide(new BigDecimal("200"), 6, RoundingMode.HALF_UP)
                .multiply(request.getDurationMinutes())
                .setScale(1, RoundingMode.HALF_UP);

        WorkoutDetail detail = new WorkoutDetail(workout, exercise, request.getDurationMinutes(),
                request.getSets(), request.getReps(), request.getWeightKg(), calories);
        WorkoutDetail saved = workoutDetailRepository.save(detail);

        // A successfully saved valid exercise creates at most one check-in for the completion date.
        checkInStreakService.registerValidWorkout(username, java.time.LocalDate.now(VN_ZONE));
        badgeService.evaluateForUser(user);
        return saved;
    }

    @Transactional
    public Workout completeWorkout(String username, Integer workoutId) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        if (!user.isEnabled() || user.isLocked()) throw new AccessDeniedException("Tài khoản không thể tập lúc này");
        Workout workout = workoutRepository.findByIdForUpdate(workoutId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy buổi tập"));
        if (!workout.getUser().getId().equals(user.getId())) throw new AccessDeniedException("Không có quyền với buổi tập này");
        if (workout.getStatus() == WorkoutStatus.COMPLETED) return workout;
        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) throw new IllegalStateException("Buổi tập đã bị hủy");
        boolean hasDetail = workoutDetailRepository.findAll().stream()
                .anyMatch(d -> d.getWorkout().getWorkoutId().equals(workoutId));
        if (!hasDetail) throw new IllegalStateException("Cần hoàn thành ít nhất một bài tập trước khi kết thúc buổi");
        workout.setStatus(WorkoutStatus.COMPLETED);
        workout.setEndTime(OffsetDateTime.now(VN_ZONE));
        Workout saved = workoutRepository.save(workout);
        badgeService.evaluateForUser(user);
        return saved;
    }
}
