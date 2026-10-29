package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="daily_fitness_records", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","activity_date"}))
@Getter
@Setter
@NoArgsConstructor
public class DailyFitnessRecord {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="user_id",nullable=false)
    private User user;

    @Column(name = "first_checked_in_at")
    private LocalDateTime firstCheckedInAt;
    
    @Column(name="activity_date",nullable=false)
    private LocalDate activityDate;

    @Column(name="rest_day_valid",nullable=false)
    private boolean restDayValid=false;

    @Column(name="checked_in",nullable=false)
    private boolean checkedIn=false;

    @Column(name="finalized",nullable=false)
    private boolean finalized=false;

    @Column(name="missed_count_after_day",nullable=false)
    private int missedCountAfterDay=0;

    @Column(name="reminder_sent",nullable=false)
    private boolean reminderSent=false;

    public DailyFitnessRecord(User user, LocalDate date){
        this.user=user;
        this.activityDate=date;
    }
}
