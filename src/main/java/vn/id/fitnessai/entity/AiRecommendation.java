package vn.id.fitnessai.entity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.*;

@Entity
@Table(name="ai_recommendations",indexes=@Index(name="idx_ai_rec_profile",columnList="profile_id,id"))

@Getter
@Setter
public class AiRecommendation {
    @Version
    @org.hibernate.annotations.ColumnDefault("0")
    private long version;

    private Long parentRecommendationId;
    private java.util.UUID sourceTurnId;
    private java.util.UUID sourceDraftId;
    private LocalDate effectiveUntil;
    private LocalDate workoutOverrideDate;
    // Explicit weekdays, e.g. "2,4,7". Empty legacy data is not a valid rest day.
    private String restDays;
    private java.math.BigDecimal targetCalories;
    private java.math.BigDecimal targetProtein;
    private java.math.BigDecimal targetCarbs;
    private java.math.BigDecimal targetFat;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="profile_id",nullable=false)
    private UserProfile profile;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="meal_log_id")
    private NutritionLog mealLog;

    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="chat_id") private
    AiChatMessage chat;

    @Column(nullable=false,length=32)
    private String type;

    @Column(nullable=false,length=180)
    private String title;

    @Column(nullable=false,columnDefinition="text")
    private String content;

    @Column(name="created_at",nullable=false)
    private OffsetDateTime createdAt=OffsetDateTime.now();

    @Column(name="read_at")
    private OffsetDateTime readAt;

    @Column(name="expired_at")
    private OffsetDateTime expiredAt;

    @Column(name="status",nullable=false,length=24)
    private String status="PROPOSED";

    @Column(name="workout_start_date")
    private LocalDate workoutStartDate;
}
