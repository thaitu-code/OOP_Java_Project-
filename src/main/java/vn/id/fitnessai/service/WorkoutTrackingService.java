package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.*;
import vn.id.fitnessai.dto.WorkoutDetailCompleteRequest;
import vn.id.fitnessai.dto.WorkoutDetailResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;



import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class WorkoutTrackingService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final WorkoutRepository workoutRepository;
    private final WorkoutDetailRepository workoutDetailRepository;
    //private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final CheckInStreakService checkInStreakService;
    private final BadgeService badgeService;
    private final Validator validator;

    @Transactional
    public Optional<WorkoutDetailResponse> completeWorkoutDetail(
            String username,
            Integer workoutId,
            Integer detailId,
            WorkoutDetailCompleteRequest request,
            Duration timeout
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Thông tin người dùng không được để trống"
            );
        }

        if (workoutId == null || workoutId <= 0) {
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }

        if (detailId == null || detailId <= 0) {
            throw new IllegalArgumentException("ID chi tiết phải lớn hơn 0");
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu hoàn thành bài không được để trống"
            );
        }

        Set<ConstraintViolation<WorkoutDetailCompleteRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.iterator().next().getMessage()
            );
        }

        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Không tìm thấy người dùng"
                        )
                );

        if (!user.isEnabled() || user.isLocked()) {
            throw new AccessDeniedException(
                    "Tài khoản chưa được xác minh hoặc đã bị khóa"
            );
        }

        Optional<Workout> workoutOptional =
                workoutRepository.findByIdForUpdate(workoutId);

        if (workoutOptional.isEmpty()) {
            return Optional.empty();
        }

        Workout workout = workoutOptional.get();

        if (!workout.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException(
                    "Bạn không có quyền hoàn thành bài trong buổi tập này"
            );
        }

        Optional<WorkoutDetail> detailOptional =
                workoutDetailRepository.findByDetailIdAndWorkout_workoutId(
                        detailId,
                        workoutId
                );

        if (detailOptional.isEmpty()) {
            return Optional.empty();
        }

        WorkoutDetail detail = detailOptional.get();

        // Gọi lại chỉ trả kết quả đã lưu, không tính hoặc check-in lại.
        if (detail.getStatus() == WorkoutDetailStatus.COMPLETED) {
            return Optional.of(toDetailResponse(detail));
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalStateException("Buổi tập đã kết thúc hoặc hủy");
        }

        UserProfile profile = user.getProfile();

        if (profile == null
                || profile.getWeightKg() == null
                || profile.getWeightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Hãy cập nhật cân nặng trong hồ sơ trước khi hoàn thành bài"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

        boolean completed = detail.complete(
                request.getTimerToken(),
                now,
                timeout,
                profile.getWeightKg(),
                request.getSets(),
                request.getReps(),
                request.getWeightKg()
        );

        // Lưu cả trường hợp complete() trả false và detail chuyển FROZEN.
        WorkoutDetail saved = workoutDetailRepository.saveAndFlush(detail);

        if (completed) {
            checkInStreakService.registerValidWorkout(
                    username,
                    saved.getCompletedAt().toLocalDate()
            );

            badgeService.evaluateForUser(user);
        }

        return Optional.of(toDetailResponse(saved));
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

        boolean hasCompletedExercise =
                workoutDetailRepository.existsByWorkout_WorkoutIdAndStatus(
                        workoutId,
                        WorkoutDetailStatus.COMPLETED
                );

        if (!hasCompletedExercise) {
            throw new IllegalStateException(
                    "Cần hoàn thành ít nhất một bài tập trước khi kết thúc buổi"
            );
        }

        workout.setStatus(WorkoutStatus.COMPLETED);
        workout.setEndTime(OffsetDateTime.now(VN_ZONE));

        Workout saved = workoutRepository.save(workout);

        badgeService.evaluateForUser(user);

        return saved;
    }

    private WorkoutDetailResponse toDetailResponse(WorkoutDetail detail) {
        return new WorkoutDetailResponse(
                detail.getDetailId(),
                detail.getWorkout().getWorkoutId(),
                detail.getExerciseIdSnapshot(),
                detail.getExerciseNameSnapshot(),
                detail.getCategoryIdSnapshot(),
                detail.getCategoryNameSnapshot(),
                detail.getMetValueSnapshot(),
                detail.getStatus(),
                detail.getDurationMinutes(),
                detail.getSets(),
                detail.getReps(),
                detail.getWeightKg(),
                detail.getCaloriesBurned(),
                detail.getStartedAt(),
                detail.getCompletedAt()
        );
    }
}
