package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import vn.id.fitnessai.enums.WorkoutDetailStatus;

import java.math.BigDecimal;
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

        this.startedAt = startedAt;
        this.status = WorkoutDetailStatus.IN_PROGRESS;

        this.lastRecordedAt = startedAt;
        this.lastHeartbeatAt = startedAt;
        this.timerToken = UUID.randomUUID();
    }
}
