package vn.id.fitnessai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
@Entity
@Table(name="user_badges",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","badge_id"}))
@Getter
@Setter
@NoArgsConstructor
public class UserBadge {

    private static final java.time.ZoneId VN_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_id",nullable=false)
    private User user;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="badge_id",nullable=false)
    private Badge badge;

    @Column(name="awarded_at",nullable=false)
    private LocalDateTime awardedAt=LocalDateTime.now(VN_ZONE);

    @Column(name="metric_value_at_award",nullable=false)
    private Double metricValueAtAward;

    public UserBadge(User user,Badge badge,Double value){
        this.user=user;
        this.badge=badge;
        this.metricValueAtAward=value;
    }
}
