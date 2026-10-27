package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.id.fitnessai.enums.WorkoutDetailStatus;

import java.math.BigDecimal;

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

    public WorkoutDetail(Workout workout, Exercise exercise) {

        if (workout == null){
            throw new IllegalArgumentException("Buổi tập không được để trống");
        }

        if (exercise == null) {
            throw new IllegalArgumentException("Bài tập không được để trống");
        }

        ExerciseCategory category = exercise.getCategory();

        if (category == null) {
            throw new IllegalArgumentException("Nhóm bài tập không được để trống");
        }

        this.workout = workout;
        this.exercise = exercise;

        // Sao chép thông tin bài tập để lưu lịch sử
        this.exerciseIdSnapshot = exercise.getExerciseId();
        this.exerciseNameSnapshot = exercise.getExerciseName();
        this.categoryIdSnapshot = category.getCategoryId();
        this.categoryNameSnapshot = category.getCategoryName();
        this.metValueSnapshot = exercise.getMetValue();

        // Kết quả thực tế của lần tập
        this.durationMinutes = BigDecimal.ZERO;
        this.caloriesBurned = BigDecimal.ZERO;
    }
}
