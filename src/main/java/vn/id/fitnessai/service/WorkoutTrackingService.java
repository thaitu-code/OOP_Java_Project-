package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.math.BigDecimal;

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

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public Optional<WorkoutTimerResponse> recordWorkoutDetailHeartbeat(
            String username,
            Integer workoutId,
            Integer detailId,
            WorkoutHeartbeatRequest request,
            Duration timeout
    ){
        if (username == null || username.isBlank()){
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }

        if (workoutId == null || workoutId <= 0){
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }

        if (detailId == null || detailId <= 0){
            throw new IllegalArgumentException("ID chi tiết phải lớn hơn 0");
        }

        if (request == null){
            throw new IllegalArgumentException("Dữ liệu heartbeat không được để trống");
        }

        Set<ConstraintViolation<WorkoutHeartbeatRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()){
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }

        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (!user.getEnabled() || user.getLocked()){
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        Optional<Workout> workoutOptional = workoutRepository.findByIdForUpdate(workoutId);

        if (workoutOptional.isEmpty()){
            return Optional.empty();
        }

        Workout workout = workoutOptional.get();

        if (!workout.getUser().getId().equals(user.getId())){
            throw new AccessDeniedException("Bạn không có quyền điều khiển timer trong buổi tập này");
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS){
            throw new IllegalStateException("Buổi tập đã kết thúc hoặc hủy");
        }

        Optional<WorkoutDetail> detailOptional = workoutDetailRepository.findByDetailIdAndWorkout_workoutId(detailId, workoutId);

        if (detailOptional.isEmpty()){
            return Optional.empty();
        }

        WorkoutDetail detail = detailOptional.get();

        detail.recordHeartbeat(request.getTimerToken(), OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS), timeout);

        WorkoutDetail saved = workoutDetailRepository.saveAndFlush(detail);

        return Optional.of(WorkoutDtoMapper.timer(saved, true));
    }

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

        OffsetDateTime now = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS);

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
        entityManager.refresh(saved);
        
        if (completed) {
            checkInStreakService.registerValidWorkout(
                    username,
                    saved.getCompletedAt()
            );

            badgeService.evaluateForUser(user);
        }

        return Optional.of(toDetailResponse(saved));
    }

    @Transactional
    public Optional<WorkoutResponse> completeWorkout(String username, Integer workoutId) {
        User user = timerUser(username);
        Optional<Workout> found = ownedWorkoutForUpdate(user, workoutId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Workout workout = found.get();
        List<WorkoutDetail> details = workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workoutId);
        if (workout.getStatus() == WorkoutStatus.COMPLETED) {
            return Optional.of(WorkoutDtoMapper.workout(workout, details));
        }
        requireOpen(workout);
        if (details.stream().noneMatch(detail -> detail.getStatus() == WorkoutDetailStatus.COMPLETED)) {
            throw new IllegalStateException("Cần hoàn thành ít nhất một bài tập trước khi kết thúc buổi");
        }
        OffsetDateTime now = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS);
        cancelUnfinishedDetails(details, now);
        workout.setStatus(WorkoutStatus.COMPLETED);
        workout.setEndTime(now);
        Workout saved = workoutRepository.saveAndFlush(workout);
        badgeService.evaluateForUser(user);
        return Optional.of(WorkoutDtoMapper.workout(saved, details));
    }

    @Transactional
    public Optional<WorkoutResponse> cancelWorkout(String username, Integer workoutId) {
        UserRepository.WorkoutAccess user = timerAccount(username);
        Optional<Workout> found = ownedWorkoutForUpdate(user.getId(), workoutId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Workout workout = found.get();
        List<WorkoutDetail> details = workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workoutId);
        if (workout.getStatus() == WorkoutStatus.CANCELLED) {
            return Optional.of(WorkoutDtoMapper.workout(workout, details));
        }
        requireOpen(workout);
        OffsetDateTime now = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS);
        cancelUnfinishedDetails(details, now);
        workout.setStatus(WorkoutStatus.CANCELLED);
        workout.setEndTime(now);
        Workout saved = workoutRepository.saveAndFlush(workout);
        // Kết quả/check-in từ bài đã hoàn thành vẫn giữ nguyên; hủy không tạo check-in mới.
        return Optional.of(WorkoutDtoMapper.workout(saved, details));
    }

    @Transactional
    public Optional<WorkoutTimerResponse> recoverWorkoutDetailTimer(
            String username,
            Integer workoutId,
            Integer detailId
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Thông tin người dùng không được để trống"
            );
        }

        if (workoutId == null || workoutId <= 0) {
            throw new IllegalArgumentException(
                    "ID buổi tập phải lớn hơn 0"
            );
        }

        if (detailId == null || detailId <= 0) {
            throw new IllegalArgumentException(
                    "ID chi tiết phải lớn hơn 0"
            );
        }

        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy người dùng"
                ));

        if (!user.getEnabled() || user.getLocked()) {
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
                    "Bạn không có quyền khôi phục timer trong buổi tập này"
            );
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Buổi tập đã kết thúc hoặc hủy"
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

        detail.recoverTimer(OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS));

        WorkoutDetail saved =
                workoutDetailRepository.saveAndFlush(detail);

        return Optional.of(WorkoutDtoMapper.timer(saved, true));
    }

    @Transactional
    public Optional<WorkoutRestApprovalResponse> approveWorkoutDetailRestDuration(
            String username,
            Integer workoutId,
            Integer detailId,
            WorkoutRestApprovalRequest request
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }

        if (workoutId == null || workoutId <= 0) {
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }

        if (detailId == null || detailId <= 0) {
            throw new IllegalArgumentException("ID chi tiết phải lớn hơn 0");
        }

        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu duyệt thời lượng nghỉ không được để trống");
        }

        Set<ConstraintViolation<WorkoutRestApprovalRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }

        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (!user.getEnabled() || user.getLocked()) {
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        Optional<Workout> workoutOptional = workoutRepository.findByIdForUpdate(workoutId);
        if (workoutOptional.isEmpty()) {
            return Optional.empty();
        }

        Workout workout = workoutOptional.get();
        if (!workout.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền thay đổi thời lượng nghỉ trong buổi tập này");
        }

        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalStateException("Buổi tập đã kết thúc hoặc hủy");
        }

        Optional<WorkoutDetail> detailOptional =
                workoutDetailRepository.findByDetailIdAndWorkout_workoutId(detailId, workoutId);
        if (detailOptional.isEmpty()) {
            return Optional.empty();
        }

        WorkoutDetail detail = detailOptional.get();
        detail.approveRestDuration(request.getRestSeconds());
        WorkoutDetail saved = workoutDetailRepository.saveAndFlush(detail);

        return Optional.of(new WorkoutRestApprovalResponse(
                saved.getDetailId(),
                saved.getWorkout().getWorkoutId(),
                saved.getApprovedRestSecondsSnapshot()
        ));
    }

    private WorkoutDetailResponse toDetailResponse(WorkoutDetail detail) {
        return WorkoutDtoMapper.detail(detail);
    }

    @Transactional
    public Optional<WorkoutTimerResponse> startWorkoutDetailRest(String username, Integer workoutId,
            Integer detailId, WorkoutHeartbeatRequest request, Duration timeout) {
        return updateTimer(username, workoutId, detailId, request, timeout, TimerAction.REST);
    }

    @Transactional
    public Optional<WorkoutTimerResponse> pauseWorkoutDetail(String username, Integer workoutId,
            Integer detailId, WorkoutHeartbeatRequest request, Duration timeout) {
        return updateTimer(username, workoutId, detailId, request, timeout, TimerAction.PAUSE);
    }

    @Transactional
    public Optional<WorkoutTimerResponse> resumeWorkoutDetail(String username, Integer workoutId,
            Integer detailId, WorkoutHeartbeatRequest request, Duration timeout) {
        return updateTimer(username, workoutId, detailId, request, timeout, TimerAction.RESUME);
    }

    private enum TimerAction { REST, PAUSE, RESUME }

    private Optional<WorkoutTimerResponse> updateTimer(String username, Integer workoutId,
            Integer detailId, WorkoutHeartbeatRequest request, Duration timeout, TimerAction action) {
        validateDetailId(detailId);
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu điều khiển timer không được để trống");
        }
        Set<ConstraintViolation<WorkoutHeartbeatRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Ngưỡng mất kết nối phải lớn hơn 0");
        }
        UserRepository.WorkoutAccess user = timerAccount(username);
        Optional<Workout> found = ownedWorkoutForUpdate(user.getId(), workoutId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        requireOpen(found.get());
        Optional<WorkoutDetail> foundDetail =
                workoutDetailRepository.findByDetailIdAndWorkout_workoutId(detailId, workoutId);
        if (foundDetail.isEmpty()) {
            return Optional.empty();
        }
        WorkoutDetail detail = foundDetail.get();
        OffsetDateTime now = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS);
        switch (action) {
            case REST -> detail.startRest(request.getTimerToken(), now, timeout);
            case PAUSE -> detail.pause(request.getTimerToken(), now, timeout);
            case RESUME -> {
                if (detail.getStatus() == WorkoutDetailStatus.RESTING) {
                    detail.resumeAfterRest(request.getTimerToken(), now);
                } else {
                    detail.resumePaused(request.getTimerToken(), now);
                }
            }
        }
        // Lưu cả nhánh REST/PAUSE trả false khi timer quá hạn và chuyển FROZEN.
        return Optional.of(WorkoutDtoMapper.timer(workoutDetailRepository.saveAndFlush(detail), true));
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutTimerResponse> getWorkoutDetailTimer(String username, Integer workoutId, Integer detailId) {
        validateDetailId(detailId);
        if (username == null || username.isBlank() || workoutId == null || workoutId <= 0) {
            throw new IllegalArgumentException("Thông tin người dùng hoặc ID buổi tập không hợp lệ");
        }
        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        requireUsable(user);
        Optional<Workout> found = workoutRepository.findById(workoutId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        if (!found.get().getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Bạn không có quyền xem timer trong buổi tập này");
        }
        // Đọc timer không tự cấp quyền điều khiển hoặc để lộ token cho tab khác.
        return workoutDetailRepository.findByDetailIdAndWorkout_workoutId(detailId, workoutId)
                .map(detail -> WorkoutDtoMapper.timer(detail, false));
    }

    @Transactional
    public void freezeExpiredWorkout(Integer workoutId, Duration timeout) {
        Optional<Long> ownerId = workoutRepository.findOwnerIdByWorkoutId(workoutId);
        if (ownerId.isEmpty()) {
            return;
        }
        if (userRepository.findByIdForUpdate(ownerId.get()).isEmpty()) {
            return;
        }
        Optional<Workout> found = workoutRepository.findByIdForUpdate(workoutId);
        if (found.isEmpty() || found.get().getStatus() != WorkoutStatus.IN_PROGRESS
                || !found.get().getUser().getId().equals(ownerId.get())) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(VN_ZONE).truncatedTo(ChronoUnit.MICROS);
        List<WorkoutDetail> changed = new ArrayList<>();
        for (WorkoutDetail detail : workoutDetailRepository.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(workoutId)) {
            if (detail.freezeIfTimedOut(now, timeout)) {
                changed.add(detail);
            }
        }
        if (!changed.isEmpty()) {
            workoutDetailRepository.saveAllAndFlush(changed);
        }
    }

    private UserRepository.WorkoutAccess timerAccount(String username) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        UserRepository.WorkoutAccess user = userRepository.findWorkoutAccessForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        requireUsable(user);
        return user;
    }

    private void requireUsable(UserRepository.WorkoutAccess user) {
        if (!user.getEnabled() || user.getLocked()) throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
    }

    private User timerUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Thông tin người dùng không được để trống");
        }
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        requireUsable(user);
        return user;
    }

    private void requireUsable(User user) {
        if (!user.isEnabled() || user.isLocked()) {
            throw new AccessDeniedException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }
    }

    private Optional<Workout> ownedWorkoutForUpdate(User user, Integer workoutId) {
        return ownedWorkoutForUpdate(user.getId(), workoutId);
    }

    private Optional<Workout> ownedWorkoutForUpdate(Long userId, Integer workoutId) {
        if (workoutId == null || workoutId <= 0) {
            throw new IllegalArgumentException("ID buổi tập phải lớn hơn 0");
        }
        Optional<Workout> found = workoutRepository.findByIdForUpdate(workoutId);
        if (found.isPresent() && !found.get().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Bạn không có quyền điều khiển buổi tập này");
        }
        return found;
    }

    private void requireOpen(Workout workout) {
        if (workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalStateException("Buổi tập đã kết thúc hoặc hủy");
        }
    }

    private void cancelUnfinishedDetails(List<WorkoutDetail> details, OffsetDateTime now) {
        List<WorkoutDetail> unfinished = details.stream()
                .filter(detail -> detail.getStatus() != WorkoutDetailStatus.COMPLETED
                        && detail.getStatus() != WorkoutDetailStatus.CANCELLED)
                .toList();
        unfinished.forEach(detail -> detail.cancel(now));
        if (!unfinished.isEmpty()) {
            workoutDetailRepository.saveAllAndFlush(unfinished);
        }
    }

    private void validateDetailId(Integer detailId) {
        if (detailId == null || detailId <= 0) {
            throw new IllegalArgumentException("ID chi tiết phải lớn hơn 0");
        }
    }
}
