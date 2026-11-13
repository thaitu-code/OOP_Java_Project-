package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.FitnessGoal;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiNotificationService {
    private final AiAccess access;
    private final AiNotificationRepository notifications;
    private final NutritionLogRepository logs;
    private final LogDetailRepository details;
    private final UserProfileRepository profiles;
    private final UserStreakRepository streaks;
    private final DailyFitnessRecordRepository days;
    private final AiScheduleService schedule;
    private final WorkoutRepository workouts;
    private final WorkoutDetailRepository workoutDetails;

    public record View(Long id, String type, String content, String link, boolean applicable, boolean read, OffsetDateTime createdAt) {}
    private View view(AiNotification n) { return new View(n.getId(), n.getType(), n.getContent(), n.getLink(), n.isApplicable(), n.getReadAt() != null, n.getCreatedAt()); }

    @Transactional
    public List<View> refresh(String username) {
        User user = access.lock(username);
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE);
        var profile = profiles.findByUserId(user.getId()).orElse(null);
        BigDecimal calories = logs.findByUser_IdAndLogDate(user.getId(), today)
                .map(l -> details.sumByLogIdAndUserId(l.getLogId(), user.getId()).getCalories()).orElse(BigDecimal.ZERO);
        boolean excess = profile != null && profile.getGoal() == FitnessGoal.LOSE_WEIGHT && profile.getCalculatedTdee() != null
                && calories != null && calories.compareTo(profile.getCalculatedTdee()) > 0;
        event(user, "CALORIE_ALERT:" + today, "CALORIE_ALERT", excess,
                "Calo đã ghi hôm nay vượt TDEE ước tính. Xem nhật ký và đối chiếu mục tiêu đã chọn.", "/nutrition");
        var streak = streaks.findByUser_Id(user.getId()).orElse(null);
        boolean checkedIn = days.findByUser_IdAndActivityDate(user.getId(), today).map(DailyFitnessRecord::isCheckedIn).orElse(false);
        boolean missed = streak != null && streak.getConsecutiveMissedDays() >= 3 && !checkedIn && !schedule.isRestDay(user.getId(), today);
        event(user, "INACTIVITY_ALERT:" + today, "INACTIVITY_ALERT", missed,
                "Bạn đã bỏ lỡ ít nhất 3 ngày phải tập. Hãy quay lại tập luyện khi bạn sẵn sàng.", "/workout");
        long remaining = workouts.findByUser_IdAndStatus(user.getId(), vn.id.fitnessai.enums.WorkoutStatus.IN_PROGRESS)
                .map(w -> workoutDetails.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(w.getWorkoutId()).stream()
                        .filter(d -> d.getStatus() != vn.id.fitnessai.enums.WorkoutDetailStatus.COMPLETED && d.getStatus() != vn.id.fitnessai.enums.WorkoutDetailStatus.CANCELLED).count()).orElse(0L);
        event(user, "WORKOUT_PENDING:" + today, "WORKOUT_PENDING", remaining > 0,
                "Buổi tập đang mở còn " + remaining + " bài chưa hoàn thành. Bạn có thể tiếp tục hoặc đóng buổi khi sẵn sàng.", "/workout");
        if (streak != null && checkedIn) {
            int n = streak.getCurrentStreak();
            if (n > 0 && (n % 7 == 0 || Set.of(30,60,90,100,365).contains(n)))
                event(user, "STREAK_MILESTONE:" + n, "STREAK_MILESTONE", true, "Chúc mừng bạn đạt streak " + n + " ngày!", "/home");
        }
        // Alerts from previous days remain readable, but no longer describe the current day.
        var all = notifications.findTop100ByUser_IdOrderByIdDesc(user.getId());
        for (var n : all) if (!n.getType().equals("STREAK_MILESTONE") && !n.getEventKey().endsWith(today.toString())) n.setApplicable(false);
        return all.stream().map(this::view).toList();
    }

    private void event(User user, String key, String type, boolean applies, String text, String link) {
        var existing = notifications.findByUser_IdAndEventKey(user.getId(), key);
        if (existing.isPresent()) { existing.get().setApplicable(applies); return; }
        if (!applies) return;
        var n = new AiNotification(); n.setUser(user); n.setEventKey(key); n.setType(type); n.setContent(text); n.setLink(link);
        notifications.save(n);
    }

    @Transactional
    public View read(String username, Long id) {
        User user = access.lock(username);
        var n = notifications.findById(id).orElseThrow(() -> new NoSuchElementException("Không tìm thấy thông báo"));
        if (!n.getUser().getId().equals(user.getId())) throw new SecurityException("Thông báo không thuộc bạn");
        n.setReadAt(OffsetDateTime.now()); return view(n);
    }
}
