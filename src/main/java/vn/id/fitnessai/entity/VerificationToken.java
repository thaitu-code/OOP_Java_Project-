package vn.id.fitnessai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.id.fitnessai.enums.TokenType;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenType tokenType;

    private LocalDateTime expiryDate;

    public VerificationToken(
            String token,
            User user,
            TokenType tokenType) {

        this.token = token;
        this.user = user;
        this.tokenType = tokenType;

        // Token xác thực email có hạn 24 giờ.
        // Token đặt lại mật khẩu có hạn 1 giờ.
        if (tokenType == TokenType.EMAIL_VERIFICATION) {
            this.expiryDate = LocalDateTime.now().plusHours(24);
        } else {
            this.expiryDate = LocalDateTime.now().plusHours(1);
        }
    }
}