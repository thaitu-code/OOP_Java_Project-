package vn.id.fitnessai.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.RegisterRequest;
import vn.id.fitnessai.entity.Role;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.entity.VerificationToken;
import vn.id.fitnessai.enums.TokenType;
import vn.id.fitnessai.repository.RoleRepository;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.VerificationTokenRepository;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenRepository tokenRepository;
    private final JavaMailSender mailSender;

    public User registerUser(RegisterRequest registerRequest){
        if(userRepository.existsByUsername(registerRequest.getUsername())){
            throw new RuntimeException("Tên đăng nhập đã tồn tại");
        }
        if(userRepository.existsByEmail(registerRequest.getEmail())){
            throw new RuntimeException("Email đã được đăng ký");
        }
        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        // Băm mật khẩu trước khi lưu
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setEnabled(false);

        // Gán role mặc định là User
        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName("ROLE_USER").orElseThrow(()
                -> new RuntimeException("Không tìm thấy Role"));
        roles.add(userRole);
        user.setRoles(roles);
        User savedUser = userRepository.save(user);

        //  TẠO TOKEN VÀ GỬI MAIL ---
        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken =
                new VerificationToken(
                        token,
                        savedUser,
                        TokenType.EMAIL_VERIFICATION
                );

        tokenRepository.save(verificationToken);
        sendVerificationEmail(savedUser.getEmail(), token);
        return savedUser;

    }
    private void sendVerificationEmail(String email, String token) {
        String url = "http://localhost:8081/verify?token=" + token; // Cập nhật port 8081 giống cấu hình của bạn
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Xác thực tài khoản Fitness AI");
        message.setText("Vui lòng click vào link sau để xác minh tài khoản của bạn: " + url);
        mailSender.send(message);
    }
    // Thêm hàm gửi mail quên mật khẩu
    public void processForgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản với email này."));

        // Tạo token mới tái sử dụng lại Entity VerificationToken
        String token = UUID.randomUUID().toString();
        VerificationToken resetToken =
                new VerificationToken(
                        token,
                        user,
                        TokenType.PASSWORD_RESET
                );
        tokenRepository.save(resetToken);

        // Gửi email
        String url = "http://localhost:8081/reset-password?token=" + token;
        org.springframework.mail.SimpleMailMessage message = new org.springframework.mail.SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Yêu cầu đặt lại mật khẩu Fitness AI");
        message.setText("Vui lòng click vào link sau để đặt lại mật khẩu của bạn: " + url);
        mailSender.send(message);
    }

    // Thêm hàm lưu mật khẩu mới
    @Transactional
    public void resetPassword(String token, String newPassword) {

        VerificationToken resetToken =
                tokenRepository.findByTokenAndTokenType(
                        token,
                        TokenType.PASSWORD_RESET
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Đường dẫn đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng."
                        )
                );

        if (resetToken.getExpiryDate() == null ||
                resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {

            throw new RuntimeException(
                    "Đường dẫn đặt lại mật khẩu đã hết hạn."
            );
        }

        if (newPassword == null || !newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            throw new RuntimeException(
                    "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường và chữ số."
            );
        }

        User user = resetToken.getUser();

        // Băm mật khẩu mới trước khi lưu
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Xóa token sau khi sử dụng thành công
        tokenRepository.delete(resetToken);
    }
    @Transactional
    public void verifyEmail(String token) {

        VerificationToken verificationToken =
                tokenRepository.findByTokenAndTokenType(
                        token,
                        TokenType.EMAIL_VERIFICATION
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Đường dẫn xác minh không hợp lệ hoặc đã được sử dụng."
                        )
                );

        if (verificationToken.getExpiryDate() == null ||
                verificationToken.getExpiryDate().isBefore(LocalDateTime.now())) {

            throw new RuntimeException(
                    "Đường dẫn xác minh đã hết hạn."
            );
        }

        User user = verificationToken.getUser();

        user.setEnabled(true);
        userRepository.save(user);

        // Xóa token sau khi xác thực thành công
        tokenRepository.delete(verificationToken);
    }
}
