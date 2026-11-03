package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter; import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name="threshold_value",nullable=false)
    private long thresholdValue;

    @Column(nullable=false)
    private boolean active=true;

    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt=LocalDateTime.now();
}