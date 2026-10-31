package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "workout_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkoutDetail {
    private static final int MAX_APPROVED_REST_SECONDS = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "detail_id")
    private Integer detailId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id",nullable = false)
    private Workout workout;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "exercise_id", nullable = true)
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false, length = 20)
    private WorkoutDetailStatus status = WorkoutDetailStatus.NOT_STARTED;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "active_duration_millis", nullable = false)
    @ColumnDefault("0")
    private long activeDurationMillis = 0L;

    @Column(name = "rest_duration_millis", nullable = false)
    @ColumnDefault("0")
    private long restDurationMillis = 0L;

    @Column(name = "last_recorded_at")
    private OffsetDateTime lastRecordedAt;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "rest_started_at")
    private OffsetDateTime restStartedAt;

    @Column(name = "rest_ends_at")
    private OffsetDateTime restEndsAt;

    @Column(name = "timer_token")
    private UUID timerToken;

    //
    @Column(name = "exercise_id_snapshot")
    private Integer exerciseIdSnapshot;

    @Column(name = "exercise_name_snapshot", length = 150)
    private String exerciseNameSnapshot;

    @Column(name = "category_id_snapshot")
    private Integer categoryIdSnapshot;

    @Column(name = "category_name_snapshot", length = 100)
    private String categoryNameSnapshot;

    @Column(name = "met_value_snapshot", precision = 4, scale = 1)
    private BigDecimal metValueSnapshot;

    @Column(name = "min_duration_minutes_snapshot", precision = 6, scale = 1)
    private BigDecimal minDurationMinutesSnapshot;

    @Column(name = "approved_rest_seconds_snapshot")
    private Integer approvedRestSecondsSnapshot;

    @Column(name = "body_weight_kg_snapshot", precision = 5, scale = 2
    )
    private BigDecimal bodyWeightKgSnapshot;

    private Integer levelIdSnapshot;

    @Column(length = 100)
    private String levelNameSnapshot;

    private Integer plannedSetsSnapshot;
    private Integer plannedRepsSnapshot;
    private Integer secondsPerSetSnapshot;
    private Integer estimatedSecondsSnapshot;
    private Integer currentSetNumber;
    private Long elapsedInSetMillis;

    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean automaticRestPhase = false;

    @Column(length = 500)
    private String mediaUrlSnapshot;
    //

    @Column(name = "duration_minutes", nullable = false, precision = 6, scale = 1)
    private BigDecimal durationMinutes;

    @Column(name = "sets")
    private Integer sets;

    @Column(name = "reps")
    private Integer reps;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "calories_burned", nullable = false, precision = 8, scale = 1)
    private BigDecimal caloriesBurned;

    // Constructor cũ: tạo chi tiết bài tập với kết quả mặc định
    public WorkoutDetail(Workout workout, Exercise exercise) {
        this(
                workout,
                exercise,
                BigDecimal.ZERO,
                null,
                null,
                null,
                BigDecimal.ZERO
        );
    }

    // Constructor mới: tạo chi tiết bài tập với kết quả thực tế
    public WorkoutDetail(
            Workout workout,
            Exercise exercise,
            BigDecimal durationMinutes,
            Integer sets,
            Integer reps,
            BigDecimal weightKg,
            BigDecimal caloriesBurned
    ) {
        if (workout == null) {
            throw new IllegalArgumentException("Buổi tập không được để trống");
        }

        if (exercise == null) {
            throw new IllegalArgumentException("Bài tập không được để trống");
        }

        ExerciseCategory category = exercise.getCategory();

        if (category == null) {
            throw new IllegalArgumentException("Nhóm bài tập không được để trống");
        }

        if (durationMinutes == null || durationMinutes.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Thời lượng tập không hợp lệ");
        }

        if (caloriesBurned == null || caloriesBurned.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Lượng calo không hợp lệ");
        }

        this.workout = workout;
        this.exercise = exercise;

        // Lưu snapshot để giữ lịch sử bài tập
        this.exerciseIdSnapshot = exercise.getExerciseId();
        this.exerciseNameSnapshot = exercise.getExerciseName();
        this.categoryIdSnapshot = category.getCategoryId();
        this.categoryNameSnapshot = category.getCategoryName();
        this.metValueSnapshot = exercise.getMetValue();

        // Lưu kết quả thực tế
        this.durationMinutes = durationMinutes;
        this.sets = sets;
        this.reps = reps;
        this.weightKg = weightKg;
        this.caloriesBurned = caloriesBurned;
    }

    public void start(OffsetDateTime startedAt){
        if (startedAt == null){
            throw new IllegalArgumentException("Thời điểm bắt đầu không được để trống");
        }

        if (this.status != WorkoutDetailStatus.NOT_STARTED){
            throw new IllegalStateException("Chỉ được bắt đầu bài đang ở trạng thái chưa bắt đầu");
        }

        if (this.startedAt != null || this.completedAt != null){
            throw new IllegalStateException("Bài tập đã có mốc thời gian, không thể bắt đầu lại");
        }

        if (this.exercise == null) {
            throw new IllegalStateException(
                    "Bài tập gốc không còn tồn tại"
            );
        }

        BigDecimal minDurationMinutes =
                this.exercise.getMinDurationMinutes();

        if (minDurationMinutes == null
                || minDurationMinutes.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Ngưỡng thời gian tối thiểu của bài tập không hợp lệ"
            );
        }

        this.minDurationMinutesSnapshot = minDurationMinutes;

        this.startedAt = startedAt;
        this.status = WorkoutDetailStatus.IN_PROGRESS;

        this.lastRecordedAt = startedAt;
        this.lastHeartbeatAt = startedAt;
        this.timerToken = UUID.randomUUID();

        if (isAutomatic()) {
            this.currentSetNumber = 1; this.elapsedInSetMillis = 0L;
            this.automaticRestPhase = false;
        }
    }

    public boolean isAutomatic() { return this.estimatedSecondsSnapshot != null; }

    public void configureAutomation(ExerciseLevel level) {
        if (this.status != WorkoutDetailStatus.NOT_STARTED || this.startedAt != null || isAutomatic())
            throw new IllegalStateException("Không được đổi cấu hình của lần tập đã chọn");

        if (level == null || !level.isActive() || level.getExercise() != this.exercise)
            throw new IllegalArgumentException("Cấp độ không hợp lệ cho bài tập");

        this.levelIdSnapshot = level.getId(); this.levelNameSnapshot = level.getName();
        this.plannedSetsSnapshot = level.getSets(); this.plannedRepsSnapshot = level.getReps();
        this.secondsPerSetSnapshot = level.getSecondsPerSet(); this.estimatedSecondsSnapshot = level.getEstimatedSeconds();
        this.approvedRestSecondsSnapshot = level.getRestSeconds();
        this.currentSetNumber = 1; this.elapsedInSetMillis = 0L;
        this.mediaUrlSnapshot = this.exercise.getMediaUrl();
    }

    public long minimumRequiredMillis() {
        // Làm tròn lên tới millisecond để không công nhận sớm hơn đúng tỷ lệ 2/3.
        if (isAutomatic()) return (this.estimatedSecondsSnapshot * 2000L + 2) / 3;
        return this.minDurationMinutesSnapshot == null ? 0 : this.minDurationMinutesSnapshot
                .multiply(BigDecimal.valueOf(60000)).setScale(0, RoundingMode.CEILING).longValueExact();
    }

    public long automaticPhaseRemainingMillis() {
        if (!isAutomatic()) return 0;
        if (this.lastRecordedAt == null) return this.secondsPerSetSnapshot * 1000L;
        if (this.automaticRestPhase && this.restEndsAt != null)
            return Math.max(0, Duration.between(this.lastRecordedAt, this.restEndsAt).toMillis());
        
        return Math.max(0, this.secondsPerSetSnapshot * 1000L - this.elapsedInSetMillis);
    }

    private void nextAutomaticSet(OffsetDateTime at) {
        this.currentSetNumber++; this.elapsedInSetMillis = 0L; this.automaticRestPhase = false;
        this.status = WorkoutDetailStatus.IN_PROGRESS; this.lastRecordedAt = at;
        this.restStartedAt = null; this.restEndsAt = null;
    }

    private boolean recordAutomaticHeartbeat(OffsetDateTime now, Duration timeout) {
        if (Duration.between(this.lastHeartbeatAt, now).compareTo(timeout) > 0) {
            if (this.automaticRestPhase) recordRestUntil(now);
            this.status = WorkoutDetailStatus.FROZEN; this.timerToken = null;
            return false;
        }
        while (this.lastRecordedAt.isBefore(now)) {
            if (this.automaticRestPhase) {
                recordRestUntil(now);
                if (!this.lastRecordedAt.isBefore(this.restEndsAt)) nextAutomaticSet(this.restEndsAt);
                else break;
            } else {
                long available = Duration.between(this.lastRecordedAt, now).toMillis();
                if (available == 0) break;
                long remaining = automaticPhaseRemainingMillis();
                boolean lastSet = this.currentSetNumber >= this.plannedSetsSnapshot;
                long consumed = lastSet ? available : Math.min(available, remaining);
                this.activeDurationMillis += consumed; this.elapsedInSetMillis += consumed;
                this.lastRecordedAt = this.lastRecordedAt.plusNanos(consumed * 1000000L);
                if (!lastSet && this.elapsedInSetMillis >= this.secondsPerSetSnapshot * 1000L) {
                    this.automaticRestPhase = true; this.status = WorkoutDetailStatus.RESTING;
                    this.restStartedAt = this.lastRecordedAt;
                    this.restEndsAt = this.lastRecordedAt.plusSeconds(this.approvedRestSecondsSnapshot);
                }
            }
        }
        this.lastHeartbeatAt = now;
        return true;
    }

    public boolean freezeIfTimedOut(OffsetDateTime now, Duration timeout){
        if (now == null){
            throw new IllegalArgumentException("Thời điểm kiểm tra không được để trống");
        }

        if (timeout == null || timeout.isZero() || timeout.isNegative()){
            throw new IllegalArgumentException("Ngưỡng mất kết nối phải lớn hơn 0");
        }

        if (this.status != WorkoutDetailStatus.IN_PROGRESS
                && !(isAutomatic() && this.status == WorkoutDetailStatus.RESTING)){
            return false;
        }

        if (this.startedAt == null || this.lastHeartbeatAt == null || this.lastRecordedAt == null
                || this.timerToken == null || this.completedAt != null){
            throw new IllegalStateException("Dữ liệu timer chưa hợp lệ");
        }

        if (now.isBefore(this.startedAt) || now.isBefore(this.lastHeartbeatAt) || now.isBefore(this.lastRecordedAt)){
            throw new IllegalStateException("Thời điểm kiểm tra không được đi lùi");
        }

        Duration heartbeatGap = Duration.between(lastHeartbeatAt, now);

        if (heartbeatGap.compareTo(timeout) <= 0){
            return false;
        }

        if (isAutomatic() && this.automaticRestPhase) recordRestUntil(now);
        this.status = WorkoutDetailStatus.FROZEN;
        timerToken = null;

        return true;
    }

    public void recordRestUntil(OffsetDateTime now){
        if (now == null){
            throw new IllegalArgumentException("Thời điểm ghi nhận không được để trống");
        }

        if (this.status != WorkoutDetailStatus.RESTING){
            throw new IllegalStateException("Bài tập không ở trạng thái nghỉ");
        }

        if (this.startedAt == null
                || this.completedAt != null
                || this.restStartedAt == null
                || this.restEndsAt == null
                || this.lastRecordedAt == null) {
            throw new IllegalStateException(
                    "Dữ liệu thời gian nghỉ chưa hợp lệ"
            );
        }

        if (!this.restEndsAt.isAfter(this.restStartedAt)
                || this.restStartedAt.isBefore(this.startedAt)
                || this.lastRecordedAt.isBefore(this.restStartedAt)
                || this.lastRecordedAt.isAfter(this.restEndsAt)
                || this.restDurationMillis < 0) {
            throw new IllegalStateException(
                    "Các mốc hoặc bộ đếm thời gian nghỉ không hợp lệ"
            );
        }

        if (now.isBefore(this.lastRecordedAt)) {
            throw new IllegalStateException(
                    "Thời điểm ghi nhận không được đi lùi"
            );
        }

        OffsetDateTime recordUntil = now.isBefore(this.restEndsAt) ? now : this.restEndsAt;

        long elapsedMillis = Duration.between(this.lastRecordedAt, recordUntil).toMillis();

        this.restDurationMillis += elapsedMillis;
        this.lastRecordedAt = recordUntil;
    }

    public boolean recordHeartbeat(
            UUID receivedToken,
            OffsetDateTime now,
            Duration timeout
    ) {
        if (receivedToken == null) {
            throw new IllegalArgumentException(
                    "Token timer không được để trống"
            );
        }

        if (now == null) {
            throw new IllegalArgumentException(
                    "Thời điểm ghi nhận không được để trống"
            );
        }

        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(
                    "Ngưỡng mất kết nối phải lớn hơn 0"
            );
        }

        if (this.status != WorkoutDetailStatus.IN_PROGRESS
                && this.status != WorkoutDetailStatus.RESTING) {
            throw new IllegalStateException(
                    "Bài tập không ở trạng thái được ghi nhận thời gian"
            );
        }

        if (this.startedAt == null
                || this.lastRecordedAt == null
                || this.lastHeartbeatAt == null
                || this.timerToken == null
                || this.completedAt != null) {
            throw new IllegalStateException(
                    "Dữ liệu timer chưa hợp lệ, cần khôi phục trước"
            );
        }

        if (!this.timerToken.equals(receivedToken)) {
            throw new IllegalStateException(
                    "Token timer không còn hợp lệ"
            );
        }

        if (now.isBefore(this.lastRecordedAt)
                || now.isBefore(this.lastHeartbeatAt)) {
            throw new IllegalStateException(
                    "Thời điểm ghi nhận không được đi lùi"
            );
        }

        if (isAutomatic()) return recordAutomaticHeartbeat(now, timeout);

        if (this.status == WorkoutDetailStatus.RESTING) {
            recordRestUntil(now);

            this.lastHeartbeatAt = now;

            return true;
        }

        if (freezeIfTimedOut(now, timeout)){
            return false;
        }

        long elapsedMillis = Duration.between(this.lastRecordedAt, now).toMillis();

        this.activeDurationMillis += elapsedMillis;

        this.lastRecordedAt = now;
        this.lastHeartbeatAt = now;

        return true;
    }

    public void approveRestDuration(Integer restSeconds) {
        if (isAutomatic()) throw new IllegalStateException("Thời gian nghỉ đã được lưu theo cấp độ đã chọn");
        if (restSeconds == null || restSeconds <= 0
                || restSeconds > MAX_APPROVED_REST_SECONDS) {
            throw new IllegalArgumentException(
                    "Thời lượng nghỉ phải từ 1 đến 300 giây"
            );
        }

        if (this.status == WorkoutDetailStatus.COMPLETED
                || this.status == WorkoutDetailStatus.CANCELLED || this.completedAt != null) {
            throw new IllegalStateException(
                    "Không được thay đổi thời lượng nghỉ của bài đã hoàn thành"
            );
        }

        if (this.workout == null || this.workout.getStatus() != WorkoutStatus.IN_PROGRESS) {
            throw new IllegalStateException("Buổi tập đã kết thúc hoặc hủy");
        }

        // Chỉ áp dụng cho lần nghỉ tiếp theo, giữ nguyên khoảng nghỉ đã bắt đầu.
        this.approvedRestSecondsSnapshot = restSeconds;
    }

    public boolean startRest(
            UUID receivedToken,
            OffsetDateTime now,
            Duration timeout
    ){
        if (isAutomatic()) throw new IllegalStateException("Bài theo cấp độ tự chuyển tập và nghỉ");
        if (now == null) {
            throw new IllegalArgumentException(
                    "Thời điểm bắt đầu nghỉ không được để trống"
            );
        }

        if (this.approvedRestSecondsSnapshot == null
                || this.approvedRestSecondsSnapshot <= 0
                || this.approvedRestSecondsSnapshot > MAX_APPROVED_REST_SECONDS) {
            throw new IllegalStateException(
                    "Chưa có thời lượng nghỉ đã duyệt hợp lệ từ 1 đến 300 giây"
            );
        }

        if (this.status != WorkoutDetailStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Chỉ được bắt đầu nghỉ khi đang tập"
            );
        }

        OffsetDateTime calculatedRestEndsAt = now.plusSeconds(this.approvedRestSecondsSnapshot);

        if (!recordHeartbeat(receivedToken, now, timeout)){
            return false;
        }

        this.restStartedAt = now;
        this.restEndsAt = calculatedRestEndsAt;
        this.status = WorkoutDetailStatus.RESTING;

        return true;
    }

    public void resumeAfterRest(
            UUID receivedToken,
            OffsetDateTime now
    ) {
        if (isAutomatic()) throw new IllegalStateException("Bài theo cấp độ tự tiếp tục khi còn kết nối; mất kết nối cần khôi phục");
        if (receivedToken == null) {
            throw new IllegalArgumentException(
                    "Token timer không được để trống"
            );
        }

        if (now == null) {
            throw new IllegalArgumentException(
                    "Thời điểm tiếp tục không được để trống"
            );
        }

        if (this.status != WorkoutDetailStatus.RESTING) {
            throw new IllegalStateException(
                    "Bài tập không ở trạng thái đang nghỉ"
            );
        }

        if (this.timerToken == null
                || this.lastRecordedAt == null
                || this.lastHeartbeatAt == null
                || this.restEndsAt == null) {
            throw new IllegalStateException(
                    "Dữ liệu timer chưa hợp lệ"
            );
        }

        if (!this.timerToken.equals(receivedToken)) {
            throw new IllegalStateException(
                    "Token timer không còn hợp lệ"
            );
        }

        if (now.isBefore(this.lastRecordedAt)
                || now.isBefore(this.lastHeartbeatAt)) {
            throw new IllegalStateException(
                    "Thời điểm tiếp tục không được đi lùi"
            );
        }

        if (now.isBefore(this.restEndsAt)) {
            throw new IllegalStateException(
                    "Chưa hết thời gian nghỉ"
            );
        }

        recordRestUntil(now);

        this.status = WorkoutDetailStatus.IN_PROGRESS;
        this.lastRecordedAt = now;
        this.lastHeartbeatAt = now;
        this.timerToken = UUID.randomUUID();
    }

    public void recoverTimer(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException(
                    "Thời điểm khôi phục không được để trống"
            );
        }

        if (this.status != WorkoutDetailStatus.IN_PROGRESS
                && this.status != WorkoutDetailStatus.FROZEN
                && this.status != WorkoutDetailStatus.PAUSED
                && this.status != WorkoutDetailStatus.RESTING) {
            throw new IllegalStateException(
                    "Bài tập không ở trạng thái được khôi phục timer"
            );
        }

        if (this.startedAt == null
                || this.completedAt != null
                || this.lastRecordedAt == null
                || this.lastHeartbeatAt == null
                || this.activeDurationMillis < 0
                || this.restDurationMillis < 0) {
            throw new IllegalStateException(
                    "Dữ liệu timer chưa hợp lệ"
            );
        }

        if ((this.status == WorkoutDetailStatus.IN_PROGRESS
                || this.status == WorkoutDetailStatus.RESTING)
                && this.timerToken == null) {
            throw new IllegalStateException(
                    "Trạng thái timer và token không đồng bộ"
            );
        }

        if (now.isBefore(this.startedAt)
                || now.isBefore(this.lastRecordedAt)
                || now.isBefore(this.lastHeartbeatAt)) {
            throw new IllegalStateException(
                    "Thời điểm khôi phục không được đi lùi"
            );
        }

        if (isAutomatic()) {
            if (this.automaticRestPhase) {
                // Nghỉ vẫn chạy offline, nhưng phần active sau hạn nghỉ không được tính bù.
                this.status = WorkoutDetailStatus.RESTING;
                recordRestUntil(now);
                if (!now.isBefore(this.restEndsAt)) nextAutomaticSet(now);
            } else if (this.status != WorkoutDetailStatus.PAUSED) {
                this.status = WorkoutDetailStatus.IN_PROGRESS;
                this.lastRecordedAt = now;
            }
            this.lastHeartbeatAt = now; this.timerToken = UUID.randomUUID();
            return;
        }

        if (this.status == WorkoutDetailStatus.RESTING) {
            recordRestUntil(now);
        } else if (this.status != WorkoutDetailStatus.PAUSED) {
            this.status = WorkoutDetailStatus.IN_PROGRESS;
            this.lastRecordedAt = now;
        }

        this.lastHeartbeatAt = now;
        this.timerToken = UUID.randomUUID();
    }

    public boolean pause(UUID receivedToken, OffsetDateTime now, Duration timeout) {
        if (this.status != WorkoutDetailStatus.IN_PROGRESS) {
            throw new IllegalStateException("Chỉ được tạm dừng khi đang tập; nghỉ giữa hiệp dùng lịch nghỉ riêng");
        }
        if (!recordHeartbeat(receivedToken, now, timeout)) {
            return false;
        }

        // Heartbeat có thể vừa đi qua ranh giới set. Pause là trạng thái active;
        // không biến một khoảng nghỉ tự động đang chạy thành active khi resume.
        if (isAutomatic() && this.automaticRestPhase) {
            return false;
        }
        this.status = WorkoutDetailStatus.PAUSED;
        return true;
    }

    public void resumePaused(UUID receivedToken, OffsetDateTime now) {
        if (receivedToken == null || now == null) {
            throw new IllegalArgumentException("Token và thời điểm tiếp tục không được để trống");
        }
        if (this.status != WorkoutDetailStatus.PAUSED || this.completedAt != null
                || this.timerToken == null || this.lastRecordedAt == null || this.lastHeartbeatAt == null) {
            throw new IllegalStateException("Bài tập không có timer tạm dừng hợp lệ");
        }
        if (!this.timerToken.equals(receivedToken)) {
            throw new IllegalStateException("Token timer không còn hợp lệ");
        }
        if (now.isBefore(this.lastRecordedAt) || now.isBefore(this.lastHeartbeatAt)) {
            throw new IllegalStateException("Thời điểm tiếp tục không được đi lùi");
        }
        this.status = WorkoutDetailStatus.IN_PROGRESS;
        this.lastRecordedAt = now;
        this.lastHeartbeatAt = now;
        this.timerToken = UUID.randomUUID();
    }

    public void cancel(OffsetDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("Thời điểm hủy không được để trống");
        }
        if (this.status == WorkoutDetailStatus.COMPLETED || this.status == WorkoutDetailStatus.CANCELLED) {
            return;
        }
        if (this.completedAt != null) {
            throw new IllegalStateException("Trạng thái bài tập không khớp mốc hoàn thành, không thể hủy");
        }
        if ((this.startedAt != null && now.isBefore(this.startedAt))
                || (this.lastHeartbeatAt != null && now.isBefore(this.lastHeartbeatAt))) {
            throw new IllegalStateException("Thời điểm hủy không được đi lùi");
        }
        // Không biến bài dở thành kết quả hợp lệ hoặc cộng thêm thời gian khi đóng buổi.
        this.status = WorkoutDetailStatus.CANCELLED;
        this.cancelledAt = now;
        this.timerToken = null;
    }

    public boolean complete(
            UUID receivedToken,
            OffsetDateTime now,
            Duration timeout,
            BigDecimal bodyWeightKg,
            Integer sets,
            Integer reps,
            BigDecimal weightKg
    ) {
        if (bodyWeightKg == null
                || bodyWeightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Cân nặng cơ thể phải lớn hơn 0"
            );
        }

        if (this.minDurationMinutesSnapshot == null
                || this.minDurationMinutesSnapshot
                .compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Chưa có ngưỡng thời gian hợp lệ cho lần tập này"
            );
        }

        if (this.metValueSnapshot == null
                || this.metValueSnapshot.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "MET snapshot không hợp lệ"
            );
        }

        if (this.activeDurationMillis < 0 || this.restDurationMillis < 0) {
            throw new IllegalStateException(
                    "Bộ đếm thời gian không hợp lệ"
            );
        }

        if (sets != null && sets < 0) {
            throw new IllegalArgumentException("Số hiệp không được âm");
        }

        if (reps != null && reps < 0) {
            throw new IllegalArgumentException(
                    "Số lần mỗi hiệp không được âm"
            );
        }

        if (weightKg != null && weightKg.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tải tập không được âm");
        }

        // Kiểm tra timer và ghi nhận khoảng thời gian cuối.
        if (!recordHeartbeat(receivedToken, now, timeout)) {
            return false;
        }

        BigDecimal millisPerMinute = BigDecimal.valueOf(60_000);

        BigDecimal totalMillis = BigDecimal.valueOf(this.activeDurationMillis)
                .add(BigDecimal.valueOf(this.restDurationMillis));

        BigDecimal requiredMillis = BigDecimal.valueOf(minimumRequiredMillis());

        // So sánh thời gian chính xác trước khi làm tròn.
        if (totalMillis.compareTo(requiredMillis) < 0) {
            throw new IllegalStateException(
                    "Chưa đủ thời gian tối thiểu để hoàn thành bài tập"
            );
        }

        BigDecimal calculatedDurationMinutes =
                totalMillis.divide(millisPerMinute, 1, RoundingMode.HALF_UP);

        BigDecimal calculatedCalories = this.metValueSnapshot
                .multiply(new BigDecimal("3.5"))
                .multiply(bodyWeightKg)
                .multiply(BigDecimal.valueOf(this.activeDurationMillis))
                .divide(
                        BigDecimal.valueOf(200).multiply(millisPerMinute),
                        1,
                        RoundingMode.HALF_UP
                );

        this.durationMinutes = calculatedDurationMinutes;
        this.caloriesBurned = calculatedCalories;
        this.bodyWeightKgSnapshot = bodyWeightKg;

        this.sets = sets;
        this.reps = reps;
        this.weightKg = weightKg;

        this.completedAt = now;
        this.status = WorkoutDetailStatus.COMPLETED;
        this.timerToken = null;

        return true;
    }
}
