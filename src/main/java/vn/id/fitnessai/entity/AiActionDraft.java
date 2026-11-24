package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_action_drafts", indexes = {@Index(columnList = "user_id,session_id,status"), @Index(columnList = "user_id,turn_id")})
@Getter @Setter
public class AiActionDraft {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "session_id", nullable = false, length = 80) private String sessionId;
    @Column(name = "turn_id", nullable = false) private UUID turnId;
    @Column(nullable = false, length = 32) private String kind;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, columnDefinition = "text") private String preview;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false, length = 24) private String status = "PENDING";
    @Column(columnDefinition = "text") private String result;
    private Long recommendationId;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(30);
    @Version private long version;
}
