package vn.id.fitnessai.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter; import java.time.LocalDate;
@Entity
@Table(name="user_streaks")
@Getter
@Setter
@NoArgsConstructor
public class UserStreak {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="user_id",nullable=false,unique=true)
    private User user;

    @Column(name = "last_check_in_date")
    private LocalDate lastCheckInDate;

    @Version
    private Long version;

    @Column(name="current_streak",nullable=false)
    private int currentStreak=0;

    @Column(name="highest_streak",nullable=false)
    private int highestStreak=0;

    @Column(name="consecutive_missed_days",nullable=false)
    private int consecutiveMissedDays=0;

    @Column(name="last_finalized_date")
    private LocalDate lastFinalizedDate;

    public UserStreak(User user){
        this.user=user;
    }
}
