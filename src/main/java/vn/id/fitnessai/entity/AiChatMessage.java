package vn.id.fitnessai.entity;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity @Table(name="chat_historigites",indexes={@Index(name="idx_ai_chat_user_id",columnList="user_id,id"),@Index(name="idx_ai_chat_context",columnList="user_id,session_id,id")})
@Getter @Setter public class AiChatMessage {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id",nullable=false) private User user;
 @Column(name="session_id",nullable=false,length=80) private String sessionId;
 @Column(name="turn_id",nullable=false) private UUID turnId;
 @Column(nullable=false,length=8) private String sender;
 @Column(nullable=false,columnDefinition="text") private String content;
 @Column(name="created_at",nullable=false) private OffsetDateTime createdAt=OffsetDateTime.now();
 @Column(name="deleted_at") private OffsetDateTime deletedAt;
 @Column(name="status",nullable=false,length=20) private String status="COMPLETE";
}
