package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "daily_meal_logs", uniqueConstraints =
        @UniqueConstraint(name = "uk_nutrition_user_date", columnNames = {"user_id", "log_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NutritionLog {
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meal_log_id")
    private Integer logId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "log_date", nullable = false, updatable = false)
    private LocalDate logDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public NutritionLog(User user, OffsetDateTime now) {
        if (user == null) throw new IllegalArgumentException("Nhật ký phải có chủ sở hữu");
        this.createdAt = NutritionValidation.time(now, null);
        this.user = user;
        this.logDate = this.createdAt.atZoneSameInstant(VN_ZONE).toLocalDate();
    }

    public void requireEditableAt(OffsetDateTime now) {
        OffsetDateTime timestamp = NutritionValidation.time(now, createdAt);
        if (!logDate.equals(timestamp.atZoneSameInstant(VN_ZONE).toLocalDate())) {
            throw new IllegalStateException("Chỉ được thay đổi nhật ký hôm nay theo giờ Việt Nam");
        }
    }
}
