package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import vn.id.fitnessai.enums.WorkoutDetailStatus;

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

    @Column(name = "body_weight_kg_snapshot", precision = 5, scale = 2
    )
    private BigDecimal bodyWeightKgSnapshot;
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

        Duration heartbeatGap =
                Duration.between(this.lastHeartbeatAt, now);

        if (heartbeatGap.compareTo(timeout) > 0) {
            this.status = WorkoutDetailStatus.FROZEN;
            this.timerToken = null;

            return false;
        }

        long elapsedMillis =
                Duration.between(this.lastRecordedAt, now).toMillis();

        if (this.status == WorkoutDetailStatus.IN_PROGRESS) {
            this.activeDurationMillis += elapsedMillis;
        } else {
            this.restDurationMillis += elapsedMillis;
        }

        this.lastRecordedAt = now;
        this.lastHeartbeatAt = now;

        return true;
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

        BigDecimal requiredMillis =
                this.minDurationMinutesSnapshot.multiply(millisPerMinute);

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
