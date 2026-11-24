package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.id.fitnessai.repository.UserRepository;

@Component @RequiredArgsConstructor @Slf4j
public class DailyReminderScheduler {
    private final UserRepository users;
    private final AiReminderReservationService reservations;
    private final DailyReminderEmailService email;

    @Scheduled(cron = "${app.reminder.daily-cron:0 0 17 * * ?}", zone = "${app.reminder.zone:Asia/Ho_Chi_Minh}")
    public void executeDailyReminderTask() {
        for (var user : users.findAllActiveUsersForReminder()) {
            try {
                var delivery = reservations.reserve(user.getUsername());
                if (delivery.isEmpty()) continue;
                try { email.sendDailyWorkoutReminder(delivery.get().email(), delivery.get().username(), delivery.get().streak()); }
                catch (RuntimeException e) { reservations.failed(delivery.get()); log.warn("Chưa gửi được nhắc tập cho user {}", user.getId()); }
            } catch (RuntimeException e) { log.warn("Chưa xử lý được nhắc tập cho user {}", user.getId()); }
        }
    }
}
