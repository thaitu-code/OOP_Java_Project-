package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.DailyFitnessRecordRepository;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.UserStreakRepository;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DailyReminderScheduler {

    private final UserRepository userRepository;
    private final UserStreakRepository userStreakRepository;
    private final DailyReminderEmailService dailyReminderEmailService;
    private final DailyFitnessRecordRepository dailyFitnessRecordRepository;

    // Chạy tự động theo cấu hình app.reminder.daily-cron trong application.properties
    @Scheduled(cron = "${app.reminder.daily-cron:0 0 17 * * ?}", zone = "${app.reminder.zone:Asia/Ho_Chi_Minh}")
    public void executeDailyReminderTask() {
        LocalDate today = LocalDate.now();
        log.info("Bắt đầu quét gửi email nhắc nhở tập luyện cho ngày: {}", today);

        List<User> activeUsers = userRepository.findAllActiveUsersForReminder();

        for (User user : activeUsers) {
            try {
                // Kiểm tra trực tiếp cột checkIn trong DailyFitnessRecord của ngày hôm nay
                boolean shouldSkipReminder = dailyFitnessRecordRepository
                        .findByUser_IdAndActivityDate(user.getId(), today)
                        .map(record -> record.isCheckedIn() || record.isRestDayValid())
                        .orElse(false);

                if (!shouldSkipReminder) {
                    // Lấy số ngày streak hiện tại
                    int currentStreak = userStreakRepository
                            .findByUser_Id(user.getId())
                            .map(streak -> streak.getCurrentStreak())
                            .orElse(0);

                    // Dùng getUsername() thay cho getFullName()
                    String displayName = user.getUsername();

                    // Gửi email
                    dailyReminderEmailService.sendDailyWorkoutReminder(user.getEmail(), displayName, currentStreak);
                }
            } catch (Exception ex) {
                log.error("Lỗi khi xử lý gửi nhắc nhở cho user id: {} (email: {})", user.getId(), user.getEmail(), ex);
            }
        }

        log.info("Hoàn thành tác vụ gửi email nhắc nhở.");
    }
}