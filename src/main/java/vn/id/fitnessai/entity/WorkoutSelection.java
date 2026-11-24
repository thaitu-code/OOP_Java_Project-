package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/** Durable receipt for the atomic wizard submission. */
@Entity @Getter @Setter
@Table(name = "workout_selections", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}))
public class WorkoutSelection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(nullable = false, length = 64) private String fingerprint;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "session_id", nullable = false) private Workout workout;
}
