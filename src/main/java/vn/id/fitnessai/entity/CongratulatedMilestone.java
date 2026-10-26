package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name="congratulated_milestones",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","milestone_value"}))
@Getter
@Setter
@NoArgsConstructor
public class CongratulatedMilestone {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_id",nullable=false)
    private User user;

    @Column(name="milestone_value",nullable=false)
    private int milestoneValue;

    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt=LocalDateTime.now();

    public CongratulatedMilestone(User user,int milestone) {
        this.user=user;
        this.milestoneValue=milestone;
    }
}
