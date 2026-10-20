package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Exercise {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "exercise_id")
    @Setter(AccessLevel.NONE)
    private Integer exerciseId;

    @Column(name = "exercise_name", nullable = false, length = 150)
    private String exerciseName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "met_value", nullable = false, precision = 4, scale = 1)
    private BigDecimal metValue;

    @Column(name = "min_duration_minutes", nullable = false, precision = 6, scale = 1)
    private BigDecimal minDurationMinutes;

    @Column(name = "recommended_sets")
    private Integer recommendedSets;

    @Column(name = "recommended_reps")
    private Integer recommendedReps;

    @Column(name = "ai_video_url", length = 500)
    private String aiVideoUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ExerciseCategory category;

    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    public Exercise(String exerciseName, BigDecimal metValue, BigDecimal minDurationMinutes, ExerciseCategory category){
        this.exerciseName = exerciseName;
        this.metValue = metValue;
        this.minDurationMinutes = minDurationMinutes;
        this.category = category;
    }

}
