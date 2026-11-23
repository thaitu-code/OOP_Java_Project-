package vn.id.fitnessai.service;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import vn.id.fitnessai.dto.RegisterRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.TokenType;
import vn.id.fitnessai.repository.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Captures serialized MIME messages without opening an SMTP connection. */
class MailSenderIdentityTest {
    private static final String FROM = "sender@example.test";
    private static final String RECIPIENT = "recipient@example.test";

    static class RecordingMailSender extends JavaMailSenderImpl {
        final List<MimeMessage> sent = new ArrayList<>();
        RecordingMailSender() { setDefaultEncoding("UTF-8"); }
        @Override protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
            try {
                for (MimeMessage message : messages) {
                    message.saveChanges();
                    var bytes = new ByteArrayOutputStream();
                    message.writeTo(bytes);
                    sent.add(new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(bytes.toByteArray())));
                }
            } catch (Exception e) { throw new AssertionError("Could not serialize test mail", e); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"FitnessAI Team", "Đội ngũ FitnessAI"})
    void allFourMailFlowsUseTheConfiguredIdentityAndPreserveTheirContent(String name) throws Exception {
        var identity = new MailSenderIdentity(FROM, name);
        var mail = new RecordingMailSender();
        var users = mock(UserRepository.class);
        var roles = mock(RoleRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var tokens = mock(VerificationTokenRepository.class);
        var role = new Role(); role.setName("ROLE_USER");
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwords.encode(anyString())).thenReturn("TEST_PASSWORD_HASH");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        var accounts = new UserService(users, roles, passwords, tokens, mail, identity);
        var request = new RegisterRequest(); request.setUsername("test-user"); request.setEmail(RECIPIENT);
        request.setPassword("TestPassword123"); request.setConfirmPassword("TestPassword123");
        User user = accounts.registerUser(request);
        when(users.findByEmail(RECIPIENT)).thenReturn(Optional.of(user));
        accounts.processForgotPassword(RECIPIENT);

        var template = mock(TemplateEngine.class);
        when(template.process(eq("daily_reminder"), any(Context.class))).thenReturn("<p>Nhắc tập HTML</p>");
        new DailyReminderEmailService(mail, template, identity).sendDailyWorkoutReminder(RECIPIENT, "Test User", 6);

        user.setId(7L); user.setEnabled(true);
        when(users.findByUsernameForUpdate("test-user")).thenReturn(Optional.of(user));
        var dailyRecords = mock(DailyFitnessRecordRepository.class);
        var streaks = mock(UserStreakRepository.class);
        var milestones = mock(CongratulatedMilestoneRepository.class);
        var streak = new UserStreak(user);
        streak.setCurrentStreak(6); streak.setHighestStreak(6);
        streak.setLastFinalizedDate(LocalDate.now(CheckInStreakService.VN_ZONE).minusDays(1));
        when(streaks.findByUser_Id(7L)).thenReturn(Optional.of(streak));
        var checkIns = new CheckInStreakService(users, dailyRecords, streaks, milestones, mock(BadgeService.class),
                mail, identity, mock(AiScheduleService.class), mock(AiNotificationService.class), mock(WorkoutRepository.class));
        checkIns.registerValidWorkout("test-user", OffsetDateTime.now(CheckInStreakService.VN_ZONE));

        assertEquals(4, mail.sent.size());
        for (MimeMessage message : mail.sent) {
            InternetAddress sender = (InternetAddress) message.getFrom()[0];
            assertEquals(FROM, sender.getAddress()); assertEquals(name, sender.getPersonal());
            assertEquals(RECIPIENT, ((InternetAddress) message.getAllRecipients()[0]).getAddress());
        }
        assertEquals("Xác thực tài khoản Fitness AI", mail.sent.get(0).getSubject());
        assertEquals("Yêu cầu đặt lại mật khẩu Fitness AI", mail.sent.get(1).getSubject());
        assertTrue(mail.sent.get(2).getSubject().contains("Đừng để đứt chuỗi"));
        assertEquals("Chúc mừng streak 7 ngày!", mail.sent.get(3).getSubject());
        var saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens, times(2)).save(saved.capture());
        assertEquals(TokenType.EMAIL_VERIFICATION, saved.getAllValues().get(0).getTokenType());
        assertEquals(TokenType.PASSWORD_RESET, saved.getAllValues().get(1).getTokenType());
        assertTrue(mail.sent.get(0).getContent().toString().contains("https://fitnessai.id.vn/verify?token=" + saved.getAllValues().get(0).getToken()));
        assertTrue(mail.sent.get(1).getContent().toString().contains("https://fitnessai.id.vn/reset-password?token=" + saved.getAllValues().get(1).getToken()));
        assertTrue(mail.sent.get(3).getContent().toString().contains("Chúc mừng! Bạn vừa đạt streak 7 ngày"));
    }

    @Test void springConfigurationResolvesTheDefaultNameAndEnvironmentOverride() throws Exception {
        for (String configuredName : List.of("FitnessAI Team", "Đội ngũ FitnessAI")) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().getPropertySources().remove("systemEnvironment");
                context.getEnvironment().getPropertySources().remove("systemProperties");
                var properties = new HashMap<String, Object>();
                properties.put("spring.mail.username", FROM);
                properties.put("app.mail.sender-name", "${MAIL_SENDER_NAME:FitnessAI Team}");
                if (!configuredName.equals("FitnessAI Team")) properties.put("MAIL_SENDER_NAME", configuredName);
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-mail-only", properties));
                context.registerBean(PropertySourcesPlaceholderConfigurer.class, PropertySourcesPlaceholderConfigurer::new);
                context.register(MailSenderIdentity.class); context.refresh();
                var message = new org.springframework.mail.SimpleMailMessage();
                context.getBean(MailSenderIdentity.class).applyTo(message);
                InternetAddress from = new InternetAddress(message.getFrom());
                assertEquals(FROM, from.getAddress()); assertEquals(configuredName, from.getPersonal());
            }
        }
    }
}
