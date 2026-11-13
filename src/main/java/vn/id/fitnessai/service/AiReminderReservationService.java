package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.DailyFitnessRecordRepository;
import java.time.*;
import java.util.Optional;

@Service @RequiredArgsConstructor
public class AiReminderReservationService {
    private final AiAccess access;
    private final CheckInStreakService checkIns;
    private final AiScheduleService schedule;
    private final AiNotificationService notifications;
    private final DailyFitnessRecordRepository days;
    public record Delivery(String username, String email, int streak, LocalDate date) {}

    @Transactional
    public Optional<Delivery> reserve(String username) {
        var user = access.lock(username);
        var summary = checkIns.getSummary(username);
        notifications.refresh(username);
        if (summary.consecutiveMissedDays() < 3 || summary.checkedInToday() || schedule.isRestDay(user.getId(), summary.today())) return Optional.empty();
        var day = days.findByUser_IdAndActivityDate(user.getId(), summary.today()).orElseGet(() -> new DailyFitnessRecord(user, summary.today()));
        if (day.isReminderSent()) return Optional.empty();
        day.setReminderSent(true); days.save(day);
        return Optional.of(new Delivery(username, user.getEmail(), summary.currentStreak(), summary.today()));
    }
    @Transactional
    public void failed(Delivery delivery) {
        var user = access.lock(delivery.username());
        days.findByUser_IdAndActivityDate(user.getId(), delivery.date()).ifPresent(d -> d.setReminderSent(false));
    }
}
