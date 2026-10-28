package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.id.fitnessai.enums.BadgeMetricType;

import java.time.LocalDateTime;

@Entity
@Table(name="badges")
@Getter
@Setter
@NoArgsConstructor
public class Badge {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false,unique=true,length=100)
    private String name;

    @Column(nullable=false,length=500)
    private String description;

    @Column(length=500)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name="metric_type",nullable=false,length=40)
    private BadgeMetricType metricType;

    @Column(name="threshold_value",nullable=false)
    private long thresholdValue;

    @Column(nullable=false)
    private boolean active=true;

    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt=LocalDateTime.now();
}