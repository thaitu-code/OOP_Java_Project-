package vn.id.fitnessai.service;

import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.dto.WorkoutDetailResponse;
import vn.id.fitnessai.dto.WorkoutResponse;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.ExerciseRepository;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.WorkoutDetailRepository;
import vn.id.fitnessai.repository.WorkoutRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkoutService {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WorkoutRepository workoutRepository;
    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;
    private final WorkoutDetailRepository workoutDetailRepository;
    private final Validator validator;

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

    @Transactional
    public Optional<WorkoutDetailResponse> addExerciseToWorkout(String username, Integer workoutId, WorkoutDetailRequest request){

        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }

        if (workoutId == null || workoutId <= 0){
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }

        if (request == null){
            throw new IllegalArgumentException("Dữ liệu bài tập không được để trống");
        }

        Set<ConstraintViolation<WorkoutDetailRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()){
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }

        //Tìm user bằng username để update
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập"));

        if (!user.isEnabled() || user.isLocked()){
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        //Khóa buổi tập để cập nhật
        Optional<Workout> workoutOptional = workoutRepository.findByIdForUpdate(workoutId);

        if (workoutOptional.isEmpty()){
            return Optional.empty();
        }

        Workout workout = workoutOptional.get();

        //Check userId để tránh thay đổi ID của người khác trong URL
        if (!workout.getUser().getId().equals(user.getId())){
            throw new AccessDeniedException("Bạn không có quyền thêm bài vào buổi tập này");
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS){
            throw new IllegalStateException("Không thể thêm bài vào buổi tập đã kết thúc hoặc đã hủy");
        }

        //Khóa và kiểm tra bài tập
        Optional<Exercise> exerciseOptional = exerciseRepository.findByIdForUpdate(request.getExerciseId());

        if (exerciseOptional.isEmpty()){
            return Optional.empty();
        }

        Exercise exercise = exerciseOptional.get();

        if (!exercise.isActive()){
            throw new IllegalStateException("Bài tập đã ngừng hoạt động, không thể thêm mới");
        }

        //Kiểm tra bài tập đã tồn tại trong workout chưa để tránh thêm trùng
        boolean alreadyAdded = workoutDetailRepository.existsByWorkout_WorkoutIdAndExercise_ExerciseId(workoutId, exercise.getExerciseId());

        if (alreadyAdded){
            throw new IllegalStateException("Bài tập này đã có trong buổi tập");
        }

        //Tạo và lưu bài tập mới
        WorkoutDetail detail = new WorkoutDetail(workout, exercise);
        WorkoutDetail savedDetail = workoutDetailRepository.save(detail);

        WorkoutDetailResponse response = new WorkoutDetailResponse(
                savedDetail.getDetailId(),
                savedDetail.getWorkout().getWorkoutId(),
                savedDetail.getExerciseIdSnapshot(),
                savedDetail.getExerciseNameSnapshot(),
                savedDetail.getCategoryIdSnapshot(),
                savedDetail.getCategoryNameSnapshot(),
                savedDetail.getMetValueSnapshot(),
                savedDetail.getStatus(),
                savedDetail.getDurationMinutes(),
                savedDetail.getSets(),
                savedDetail.getReps(),
                savedDetail.getWeightKg(),
                savedDetail.getCaloriesBurned()
        );

        return Optional.of(response);
    }
}
