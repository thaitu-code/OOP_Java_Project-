package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exercise_levels", uniqueConstraints = @UniqueConstraint(columnNames = {"exercise_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExerciseLevel {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false) private int sets;
    private Integer reps;
    @Column(nullable = false) private int secondsPerSet;
    @Column(nullable = false) private int restSeconds;
    @Column(nullable = false) private int estimatedSeconds;
    @Column(nullable = false) private boolean active = true;

    public ExerciseLevel(Exercise exercise, String name, int sets, Integer reps,
                         int secondsPerSet, int restSeconds, int estimatedSeconds) {
        this.exercise = java.util.Objects.requireNonNull(exercise);
        configure(name, sets, reps, secondsPerSet, restSeconds, estimatedSeconds);
    }
    public void configure(String name, int sets, Integer reps, int secondsPerSet, int restSeconds, int estimatedSeconds) {
        if (name == null || name.isBlank() || name.strip().length() > 100 || sets < 1 || sets > 100
                || (reps != null && reps < 1) || secondsPerSet < 1 || secondsPerSet > 86400
                || restSeconds < 1 || restSeconds > 300 || estimatedSeconds < 1 || estimatedSeconds > 86400) {
            throw new IllegalArgumentException("Cấu hình cấp độ không hợp lệ");
        }
        this.name = name.strip(); this.sets = sets; this.reps = reps;
        this.secondsPerSet = secondsPerSet; this.restSeconds = restSeconds; this.estimatedSeconds = estimatedSeconds;
    }
    public void setActive(boolean active) { this.active = active; }
}
