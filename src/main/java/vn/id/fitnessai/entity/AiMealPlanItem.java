package vn.id.fitnessai.entity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import vn.id.fitnessai.enums.MealType;

@Entity
@Table(name="ai_meal_plan_items")
@Getter
@Setter
public class AiMealPlanItem {
    // Copies of a revised plan retain their origin to prevent logging the same planned meal twice.
    private Long sourceItemId;
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="recommendation_id",nullable=false)
    private AiRecommendation recommendation;

    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="food_id",nullable=false)
    private Food food;

    @Column(name="plan_date",nullable=false)
    private LocalDate planDate;

    @Enumerated(EnumType.STRING)
    @Column(name="meal_type",nullable=false,length=16)
    private MealType mealType;
    
    @Column(name="weight_grams",nullable=false,precision=9,scale=2)
    private BigDecimal grams;
    @Column(name="calories",nullable=false,precision=12,scale=2)
    private BigDecimal calories;
    @Column(name="protein",nullable=false,precision=12,scale=2)
    private BigDecimal protein;
    @Column(name="carbs",nullable=false,precision=12,scale=2)
    private BigDecimal carbs;
    @Column(name="fat",nullable=false,precision=12,scale=2)
    private BigDecimal fat;
}
