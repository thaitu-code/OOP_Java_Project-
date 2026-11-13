package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ai_notifications", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_key"}), indexes = @Index(columnList = "user_id,id"))
@Getter @Setter
public class AiNotification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "event_key", nullable = false, length = 100) private String eventKey;
    @Column(nullable = false, length = 32) private String type;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false) private String link;
    @Column(nullable = false) private boolean applicable = true;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime readAt;
}
