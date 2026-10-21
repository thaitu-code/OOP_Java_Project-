package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import vn.id.fitnessai.enums.ActivityLevel;
import vn.id.fitnessai.enums.FitnessGoal;
import vn.id.fitnessai.enums.Gender;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Setter
@Getter
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id")
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "height_cm", precision = 5, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 30)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal", length = 30)
    private FitnessGoal goal;

    @Column(name = "calculated_bmr", precision = 7, scale = 2)
    private BigDecimal calculatedBmr;

    @Column(name = "calculated_tdee", precision = 7, scale = 2)
    private BigDecimal calculatedTdee;

    @Column(name = "profile_calculated_at")
    private LocalDateTime profileCalculatedAt;

}
