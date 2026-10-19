package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExerciseCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;

    @Setter
    @Column(name = "category_name", nullable = false, unique = true, length = 100)
    private String categoryName;

    @Setter
    @Column(name = "is_active", nullable = false)
    @ColumnDefault("true")
    private boolean active = true;

    public ExerciseCategory(String categoryName){
        this.categoryName = categoryName;
    }
}
