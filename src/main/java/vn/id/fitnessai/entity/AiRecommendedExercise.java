package vn.id.fitnessai.entity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

@Entity
@Table(name="ai_recommended_exercises")
@Getter
@Setter
public class AiRecommendedExercise {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="recommendation_id",nullable=false)
    private AiRecommendation recommendation;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="exercise_id",nullable=false)
    private Exercise exercise;

    @Column(name="day_of_week",nullable=false)
    private Integer dayOfWeek;

    @Column(name="position",nullable=false)
    private Integer position;

    @Column(name="planned_sets")
    private Integer plannedSets;

    @Column(name="planned_reps")
    private Integer plannedReps;
}
