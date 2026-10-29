package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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

    /**
     * Call only after a valid workout detail has been persisted.
     * User row lock serializes simultaneous requests from multiple tabs.
     */
    @Transactional
    public void registerValidWorkout(String username, LocalDate completedDate) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        if (!user.isEnabled() || user.isLocked()) {
            throw new IllegalStateException("Tài khoản chưa được xác minh hoặc đã bị khóa");
        }

        LocalDate today = LocalDate.now(VN_ZONE);
        if (completedDate == null || !completedDate.equals(today)) {
            throw new IllegalStateException("Check-in chỉ được ghi nhận cho ngày hoàn thành thực tế theo giờ Việt Nam");
        }

        DailyFitnessRecord record = dailyRecordRepository
                .findByUser_IdAndActivityDate(user.getId(), completedDate)
                .orElseGet(() -> new DailyFitnessRecord(user, completedDate));

        if (record.isCheckedIn()) {
            return; // one check-in per user/day
        }

        record.setCheckedIn(true);
        record.setFirstCheckedInAt(LocalDateTime.now(VN_ZONE));
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

    /**
     * The AI schedule integration should call this only after a rest day is accepted and effective.
     * Do not expose this method as a user-controlled endpoint.
     */
    @Transactional
    public void markValidRestDay(String username, LocalDate date) {
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        LocalDate today = LocalDate.now(VN_ZONE);
        if (date == null || date.isBefore(today)) {
            throw new IllegalArgumentException("Không được đổi ngày trong quá khứ thành ngày nghỉ");
        }
        DailyFitnessRecord record = dailyRecordRepository
                .findByUser_IdAndActivityDate(user.getId(), date)
                .orElseGet(() -> new DailyFitnessRecord(user, date));
        record.setRestDayValid(true);
        dailyRecordRepository.save(record);
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
            sendMissedWorkoutReminderIfNeeded(lockedUser, streak, yesterday.plusDays(1));
        }
    }


    private void sendMissedWorkoutReminderIfNeeded(User user, UserStreak streak, LocalDate targetDate) {
        if (streak.getConsecutiveMissedDays() < 3 || user.isLocked() || !user.isEnabled()) return;
        DailyFitnessRecord target = dailyRecordRepository
                .findByUser_IdAndActivityDate(user.getId(), targetDate)
                .orElseGet(() -> new DailyFitnessRecord(user, targetDate));
        if (target.isRestDayValid() || target.isReminderSent()) return;
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("FitnessAI - Nhắc nhở luyện tập");
            message.setText("Bạn đã bỏ lỡ " + streak.getConsecutiveMissedDays()
                    + " ngày tập bắt buộc liên tiếp. Hãy hoàn thành một bài tập hợp lệ hôm nay để bắt đầu lại chuỗi nhé!");
            mailSender.send(message);
            target.setReminderSent(true);
            dailyRecordRepository.save(target);
        } catch (RuntimeException ignored) {
            // Keep day-finalization idempotent if the mail provider is temporarily unavailable.
        }
    }

    @Transactional
    public StreakSummary getSummary(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        UserStreak streak = userStreakRepository.findByUser_Id(user.getId())
                .orElseGet(() -> new UserStreak(user));
        // Dashboard reads must not show a stale streak if midnight job was down.
        reconcileUserUntil(user, streak, LocalDate.now(VN_ZONE).minusDays(1));
        userStreakRepository.save(streak);
        LocalDate today = LocalDate.now(VN_ZONE);
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
        return dailyRecordRepository.findByUser_IdAndActivityDateBetweenOrderByActivityDateAsc(
                        user.getId(), LocalDate.of(2000, 1, 1), endDate)
                .stream().map(DailyFitnessRecord::getActivityDate).min(LocalDate::compareTo)
                .orElse(null);
    }

    public record StreakSummary(int currentStreak, int highestStreak, int consecutiveMissedDays,
                                boolean checkedInToday, LocalDate today) {}
}
