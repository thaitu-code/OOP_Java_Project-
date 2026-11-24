package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_turns", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}),
        indexes = {@Index(columnList = "user_id,created_at"), @Index(columnList = "user_id,status,created_at")})
@Getter @Setter
public class AiChatTurn {
    @Id private UUID id = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(nullable = false, length = 80) private String sessionId;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false, length = 24) private String status = "PENDING";
    @Column(columnDefinition = "text") private String error;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt = OffsetDateTime.now();
    @org.hibernate.annotations.ColumnDefault("1")
    private int attempts = 1;
}
