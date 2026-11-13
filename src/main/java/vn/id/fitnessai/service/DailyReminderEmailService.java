package vn.id.fitnessai.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyReminderEmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine; // Thymeleaf TemplateEngine có sẵn trong Spring

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendDailyWorkoutReminder(String toEmail, String fullName, int currentStreak) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "FitnessAI Team");
            helper.setTo(toEmail);
            helper.setSubject("🔥 [FitnessAI] Đừng để đứt chuỗi! Hãy hoàn thành mục tiêu hôm nay");

            // Truyền dữ liệu vào template Thymeleaf
            Context context = new Context();
            context.setVariable("name", (fullName != null && !fullName.isBlank()) ? fullName : "bạn");
            context.setVariable("streak", currentStreak);

            // Render file mail/daily_reminder.html thành chuỗi HTML
            String htmlContent = templateEngine.process("daily_reminder", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Đã gửi email nhắc nhở thành công cho: {}", toEmail);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email nhắc nhở tới {}: {}", toEmail, e.getMessage());
            throw new IllegalStateException("Chưa gửi được email nhắc tập", e);
        }
    }
}
