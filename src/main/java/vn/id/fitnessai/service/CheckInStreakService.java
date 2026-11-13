package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;

import java.time.*;

@Service
@RequiredArgsConstructor
public class CheckInStreakService {
    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final DailyFitnessRecordRepository dailyRecordRepository;
    private final UserStreakRepository userStreakRepository;
    private final CongratulatedMilestoneRepository milestoneRepository;
    private final BadgeService badgeService;
    private final JavaMailSender mailSender;
    private final AiScheduleService schedule;
    private final AiNotificationService notifications;
    private final WorkoutRepository workouts;

    /**
     * Call only after a valid workout detail has been persisted.
     * User row lock serializes simultaneous requests from multiple tabs.
     */
    @Transactional
    public void registerValidWorkout(String username, OffsetDateTime completedAt) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (!user.isEnabled() || user.isLocked()) {
            throw new IllegalStateException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        if (completedAt == null){
            throw new IllegalArgumentException("Thời điểm hoàn thành không được để trống");
        }

        LocalDateTime completionTimeVN = completedAt.atZoneSameInstant(VN_ZONE).toLocalDateTime();

        LocalDate completedDate = completionTimeVN.toLocalDate();

        DailyFitnessRecord record = dailyRecordRepository
                .findByUser_IdAndActivityDate(user.getId(), completedDate)
                .orElseGet(() -> new DailyFitnessRecord(user, completedDate));

        if (record.isCheckedIn()) {
            return; // one check-in per user/day
        }

        record.setCheckedIn(true);
        record.setFirstCheckedInAt(completionTimeVN);
        dailyRecordRepository.save(record);

        UserStreak streak = userStreakRepository.findByUser_Id(user.getId())
                .orElseGet(() -> new UserStreak(user));

        // Finalize every previous date first so a missed required day cannot be skipped.
        reconcileUserUntil(user, streak, completedDate.minusDays(1));

        // A check-in today starts at 1 after a reset; otherwise extends the current chain.
        streak.setCurrentStreak(streak.getCurrentStreak() + 1);
        streak.setHighestStreak(Math.max(streak.getHighestStreak(), streak.getCurrentStreak()));
        streak.setConsecutiveMissedDays(0);
        streak.setLastCheckInDate(completedDate);
        streak.setLastFinalizedDate(completedDate.minusDays(1));
        userStreakRepository.save(streak);

        evaluateMilestone(user, streak);
        badgeService.evaluateForUser(user);
        notifications.refresh(username);
    }

    private void evaluateMilestone(User user, UserStreak streak) {
        int value = streak.getCurrentStreak();
        boolean milestone = value > 0 && (value % 7 == 0 || value == 30 || value == 60
                || value == 90 || value == 100 || value == 365);
        if (milestone && !milestoneRepository.existsByUser_IdAndMilestoneValue(user.getId(), value)) {
            milestoneRepository.save(new CongratulatedMilestone(user, value));
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject("Chúc mừng streak " + value + " ngày!");
                message.setText("Chúc mừng! Bạn vừa đạt streak " + value
                        + " ngày trên FitnessAI. Đây là mốc chỉ được chúc mừng một lần cho tài khoản của bạn.");
                mailSender.send(message);
            } catch (RuntimeException ignored) {
                // Keep the durable milestone mark even if email delivery fails.
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void reconcileAllUsers() {
        LocalDate yesterday = LocalDate.now(VN_ZONE).minusDays(1);
        for (User user : userRepository.findAll()) {
            User lockedUser = userRepository.findByUsernameForUpdate(user.getUsername()).orElse(null);
            if (lockedUser == null) continue;
            UserStreak streak = userStreakRepository.findByUser_Id(lockedUser.getId())
                    .orElseGet(() -> new UserStreak(lockedUser));
            reconcileUserUntil(lockedUser, streak, yesterday);
            userStreakRepository.save(streak);
        }
    }


    @Transactional
    public StreakSummary getSummary(String username) {

        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        LocalDate today = LocalDate.now(VN_ZONE);

        UserStreak streak = userStreakRepository.findByUser_Id(user.getId())
                .orElseGet(() -> new UserStreak(user));

        // Dashboard reads must not show a stale streak if midnight job was down.
        reconcileUserUntil(user, streak, today.minusDays(1));
        userStreakRepository.save(streak);

        boolean checkedIn = dailyRecordRepository.findByUser_IdAndActivityDate(user.getId(), today)
                .map(DailyFitnessRecord::isCheckedIn).orElse(false);

        return new StreakSummary(streak.getCurrentStreak(), streak.getHighestStreak(),
                streak.getConsecutiveMissedDays(), checkedIn, today);
    }

    private void reconcileUserUntil(User user, UserStreak streak, LocalDate endDate) {
        LocalDate start = streak.getLastFinalizedDate() == null
                ? findEarliestRelevantDate(user, endDate)
                : streak.getLastFinalizedDate().plusDays(1);

        if (start == null || start.isAfter(endDate)) return;

        for (LocalDate date = start; !date.isAfter(endDate); date = date.plusDays(1)) {
            final LocalDate processingDate = date;
            DailyFitnessRecord record = dailyRecordRepository
                    .findByUser_IdAndActivityDate(user.getId(), processingDate)
                    .orElseGet(() -> new DailyFitnessRecord(user, processingDate));
            if (!record.isFinalized()) {
                record.setRestDayValid(schedule.isRestDay(user.getId(), date));
                if (record.isCheckedIn()) {
                    streak.setConsecutiveMissedDays(0);
                } else if (!record.isRestDayValid()) {
                    streak.setCurrentStreak(0);
                    streak.setConsecutiveMissedDays(streak.getConsecutiveMissedDays() + 1);
                }
                record.setFinalized(true);
                record.setMissedCountAfterDay(streak.getConsecutiveMissedDays());
                dailyRecordRepository.save(record);
            }
            streak.setLastFinalizedDate(date);
        }
    }

    private LocalDate findEarliestRelevantDate(User user, LocalDate endDate) {
        var dates = new java.util.ArrayList<LocalDate>();
        schedule.firstDate(user.getId()).ifPresent(dates::add);
        workouts.findFirstByUser_IdOrderByStartTimeAsc(user.getId()).ifPresent(w -> dates.add(w.getStartTime().atZoneSameInstant(VN_ZONE).toLocalDate()));
        dailyRecordRepository.findByUser_IdAndActivityDateBetweenOrderByActivityDateAsc(
                        user.getId(), LocalDate.of(2000, 1, 1), endDate)
                .stream().map(DailyFitnessRecord::getActivityDate).forEach(dates::add);
        return dates.stream().filter(d -> !d.isAfter(endDate)).min(LocalDate::compareTo).orElse(null);
    }

    public record StreakSummary(int currentStreak, int highestStreak, int consecutiveMissedDays,
                                boolean checkedInToday, LocalDate today) {}
}
