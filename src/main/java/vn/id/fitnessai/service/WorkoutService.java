package vn.id.fitnessai.service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.dto.WorkoutDetailResponse;
import vn.id.fitnessai.dto.WorkoutResponse;
import vn.id.fitnessai.dto.WorkoutTimerResponse;
import vn.id.fitnessai.dto.WorkoutPageResponse;
import vn.id.fitnessai.dto.WorkoutSessionStateResponse;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.entity.Workout;
import vn.id.fitnessai.entity.WorkoutDetail;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.ExerciseRepository;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.WorkoutDetailRepository;
import vn.id.fitnessai.repository.WorkoutRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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

        UserRepository.WorkoutAccess access = lockedWorkoutUser(username);
        User user = userRepository.getReferenceById(access.getId());

        boolean hasActiveWorkout = workoutRepository.existsByUser_IdAndStatus(user.getId(), WorkoutStatus.IN_PROGRESS);

        if (hasActiveWorkout){
            throw new IllegalStateException("Bạn đang có một buổi tập chưa kết thúc");
        }

        Workout workout = new Workout(user, OffsetDateTime.now(VIETNAM_ZONE).truncatedTo(ChronoUnit.MICROS));

        Workout savedWorkout = workoutRepository.save(workout);

        return WorkoutDtoMapper.workout(savedWorkout, List.of());
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

        if ((request.getPlannedSets() == null) != (request.getPlannedReps() == null)) {
            throw new IllegalArgumentException("Cần truyền cả số hiệp và số lần tập dự kiến");
        }

        //Tìm user bằng username để update
        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập"));

        if (!user.getEnabled() || user.getLocked()){
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
        if (request.getLevelId() != null) {
            var level = exercise.getLevels().stream()
                    .filter(l -> l.getId().equals(request.getLevelId()) && l.isActive()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Cấp độ không thuộc bài tập hoặc đã ngừng hoạt động"));
            detail.configureAutomation(level);
        } else if (!exercise.getLevels().isEmpty()) {
            throw new IllegalArgumentException("Hãy chọn cấp độ cho bài tập");
        }
        if (request.getPlannedSets() != null) {
            detail.applyPlannedWork(request.getPlannedSets(), request.getPlannedReps());
        }
        WorkoutDetail savedDetail = workoutDetailRepository.save(detail);

        return Optional.of(WorkoutDtoMapper.detail(savedDetail));
    }

    @Transactional
    public Optional<List<WorkoutDetailResponse>> getWorkoutDetails(String username, Integer workoutId){
        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }

        if (workoutId == null || workoutId <=0){
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }

        UserRepository.WorkoutAccess user = readableUser(username);

        if (user.getLocked() || !user.getEnabled()){
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        Optional<Workout> workoutOptional = workoutRepository.findById(workoutId);

        if (workoutOptional.isEmpty()){
            return Optional.empty();
        }

        Workout workout = workoutOptional.get();

        //Kiểm tra danh tính
        if (!workout.getUser().getId().equals(user.getId())){
            throw new AccessDeniedException("Bạn không có quyền xem buổi tập này");
        }

        //Lấy các bài tập trong buổi tập theo ID tăng dần
        List<WorkoutDetail> details = workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workoutId);

        return Optional.of(details.stream().map(WorkoutDtoMapper::detail).toList());
    }

    @Transactional
    public Optional<WorkoutTimerResponse> startWorkoutDetail(String username, Integer workoutId, Integer detailId){
        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }

        if (workoutId == null || workoutId <= 0){
            throw new IllegalArgumentException("ID buổi tập phải lớn hớn không");
        }

        if (detailId == null || detailId <= 0){
            throw new IllegalArgumentException("ID chi tiết bài tập phải lớn hơn 0");
        }

        //Tìm và khóa tài khoản để cập nhật (tránh xung đột dữ liệu)
        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username).orElseThrow(()->
                new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập"));    //Trả về 400

        if (user.getLocked() || !user.getEnabled()){
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");    //Trả về 409
        }

        //Khóa và kiểm tra buổi tập
        Optional<Workout> workoutOptional = workoutRepository.findByIdForUpdate(workoutId);

        if (workoutOptional.isEmpty()){
            return Optional.empty();    //Trả 404
        }

        Workout workout = workoutOptional.get();

        if (!workout.getUser().getId().equals(user.getId())){
            throw new AccessDeniedException("Bạn không có quyền bắt đầu bài tập trong buổi tập này");
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS){
            throw new IllegalStateException("Buổi tập đã kết thúc hoặc hủy");
        }

        //Tìm chi tiết thuộc đúng buổi tập
        Optional<WorkoutDetail> detailOptional = workoutDetailRepository.findByDetailIdAndWorkout_workoutId(detailId, workoutId);

        if (detailOptional.isEmpty()){
            return Optional.empty();
        }

        WorkoutDetail detail = detailOptional.get();

        //Chỉ được bắt đầu bài tập đang ở trạng thái chưa bắt đầu
        if (detail.getStatus() != WorkoutDetailStatus.NOT_STARTED){
            throw new IllegalStateException("Bài tập này không ở trạng thái chưa bắt đầu");
        }

        //Kiểm tra bài tập gốc còn khả dụng không trước khi bắt đầu
        if (detail.getExercise() ==null){
            throw new IllegalStateException("Bài tập gốc không còn tồn tại");
        }

        Integer exerciseId = detail.getExercise().getExerciseId();
        Optional<Exercise> exerciseOptional = exerciseRepository.findByIdForUpdate(exerciseId);

        if (exerciseOptional.isEmpty()){
            throw new IllegalStateException("Bài tập gốc không còn tồn tại");
        }

        if (!exerciseOptional.get().isActive()){
            throw new IllegalStateException("Bài tập đã ngừng hoạt động, không thể bắt đầu");
        }

        //Kiểm tra người dùng có bài đang thực hiện không
        boolean hasUnfinishedExercise = workoutDetailRepository.existsByWorkout_User_IdAndWorkout_StatusAndStatusIn(
                user.getId(),
                WorkoutStatus.IN_PROGRESS,
                EnumSet.of(
                        WorkoutDetailStatus.IN_PROGRESS,
                        WorkoutDetailStatus.RESTING,
                        WorkoutDetailStatus.PAUSED,
                        WorkoutDetailStatus.FROZEN
                )
        );

        if (hasUnfinishedExercise){
            throw new IllegalStateException("Bạn phải hoàn thành bài đang thực hiện trước");
        }

        //Tạo thời gian
        detail.start(OffsetDateTime.now(VIETNAM_ZONE).truncatedTo(ChronoUnit.MICROS));

        WorkoutDetail savedDetail = workoutDetailRepository.save(detail);

        return Optional.of(WorkoutDtoMapper.timer(savedDetail, true));
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutResponse> getWorkout(String username, Integer workoutId) {
        UserRepository.WorkoutAccess user = readableUser(username);
        if (workoutId == null || workoutId <= 0) {
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }
        return workoutRepository.findById(workoutId).map(workout -> {
            if (!workout.getUser().getId().equals(user.getId())) {
                throw new AccessDeniedException("Bạn không có quyền xem buổi tập này");
            }
            return WorkoutDtoMapper.workout(workout,
                    workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workoutId));
        });
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutResponse> getCurrentWorkout(String username) {
        UserRepository.WorkoutAccess user = readableUser(username);
        return workoutRepository.findByUser_IdAndStatus(user.getId(), WorkoutStatus.IN_PROGRESS)
                .map(workout -> WorkoutDtoMapper.workout(workout,
                        workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workout.getWorkoutId())));
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutSessionStateResponse> getCurrentWorkoutState(String username) {
        UserRepository.WorkoutAccess user = readableUser(username);
        return workoutRepository.findByUser_IdAndStatus(user.getId(), WorkoutStatus.IN_PROGRESS).map(workout -> {
            List<WorkoutDetail> details = workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workout.getWorkoutId());
            WorkoutTimerResponse timer = details.stream()
                    .filter(d -> EnumSet.of(WorkoutDetailStatus.IN_PROGRESS, WorkoutDetailStatus.RESTING,
                            WorkoutDetailStatus.PAUSED, WorkoutDetailStatus.FROZEN).contains(d.getStatus()))
                    .findFirst().map(d -> WorkoutDtoMapper.timer(d, false)).orElse(null);
            return new WorkoutSessionStateResponse(WorkoutDtoMapper.workout(workout, details),
                    details.stream().map(WorkoutDtoMapper::detail).toList(), timer);
        });
    }

    @Transactional(readOnly = true)
    public WorkoutPageResponse getWorkouts(String username, int page, int size, WorkoutStatus status) {
        UserRepository.WorkoutAccess user = readableUser(username);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Trang phải từ 0 và kích thước trang từ 1 đến 100");
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("workoutId")));
        Page<Workout> workouts = status == null
                ? workoutRepository.findByUser_Id(user.getId(), pageable)
                : workoutRepository.findByUser_IdAndStatus(user.getId(), status, pageable);
        Map<Integer, WorkoutDetailRepository.WorkoutTotals> totals = new HashMap<>();
        if (workouts.hasContent()) {
            List<Integer> ids = workouts.getContent().stream().map(Workout::getWorkoutId).toList();
            for (WorkoutDetailRepository.WorkoutTotals total : workoutDetailRepository.sumCompletedByWorkoutIds(ids)) {
                totals.put(total.getWorkoutId(), total);
            }
        }
        List<WorkoutResponse> content = workouts.getContent().stream().map(workout -> {
            WorkoutDetailRepository.WorkoutTotals total = totals.get(workout.getWorkoutId());
            return WorkoutDtoMapper.workout(workout, total == null ? BigDecimal.ZERO : total.getDurationMinutes(),
                    total == null ? BigDecimal.ZERO : total.getCaloriesBurned());
        }).toList();
        return new WorkoutPageResponse(content, page, size, workouts.getTotalElements(), workouts.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Optional<List<WorkoutDetailResponse>> getRemainingWorkoutDetails(String username, Integer workoutId) {
        return getWorkoutDetails(username, workoutId).map(details -> details.stream()
                .filter(detail -> detail.getStatus() != WorkoutDetailStatus.COMPLETED
                        && detail.getStatus() != WorkoutDetailStatus.CANCELLED)
                .toList());
    }

    private UserRepository.WorkoutAccess lockedWorkoutUser(String username) {
        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập"));
        if (!user.getEnabled() || user.getLocked()) {
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }
        return user;
    }

    private UserRepository.WorkoutAccess readableUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }
        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản đăng nhập"));
        if (!user.getEnabled() || user.getLocked()) {
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }
        return user;
    }
}
