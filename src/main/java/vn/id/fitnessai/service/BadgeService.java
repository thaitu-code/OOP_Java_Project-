package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.BadgeMetricType;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.*;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BadgeService {
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final UserStreakRepository userStreakRepository;
    private final WorkoutRepository workoutRepository;
    private final WorkoutDetailRepository workoutDetailRepository;

    @Transactional
    public void evaluateForUser(User user) {
        List<Badge> activeBadges = badgeRepository.findByActiveTrueOrderByIdAsc();
        for (Badge badge : activeBadges) {
            if (userBadgeRepository.existsByUser_IdAndBadge_Id(user.getId(), badge.getId())) continue;
            double metric = metricValue(user, badge.getMetricType());
            if (metric >= badge.getThresholdValue()) {
                userBadgeRepository.save(new UserBadge(user, badge, metric));
            }
        }
    }

    @Transactional
    public void backfillAllUsersForBadge(Badge badge) {
        for (User user : workoutUsersAndAllUsers()) {
            if (badge.isActive() && !userBadgeRepository.existsByUser_IdAndBadge_Id(user.getId(), badge.getId())) {
                double metric = metricValue(user, badge.getMetricType());
                if (metric >= badge.getThresholdValue()) {
                    userBadgeRepository.save(new UserBadge(user, badge, metric));
                }
            }
        }
    }

    private List<User> workoutUsersAndAllUsers() {
        // User repository is injected through a helper to keep all users eligible for backfill.
        return userRepository.findAll();
    }

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public double metricValue(User user, BadgeMetricType type) {
        return switch (type) {
            case STREAK_DAYS -> userStreakRepository.findByUser_Id(user.getId())
                    .map(s -> (double) s.getHighestStreak()).orElse(0.0);
            case TOTAL_WORKOUTS -> (double) workoutRepository.countByUser_IdAndStatus(user.getId(), WorkoutStatus.COMPLETED);
            case TOTAL_CALORIES_BURNED -> {
                BigDecimal total = workoutDetailRepository.sumCaloriesByUserId(user.getId());
                yield total == null ? 0.0 : total.doubleValue();
            }
            case MAX_WEIGHT_LIFTED -> workoutDetailRepository.findMaxWeightByUserId(user.getId())
                    .map(BigDecimal::doubleValue).orElse(0.0);
            // The project does not yet contain meal-log entities; don't award this metric until that module exists.
            case TOTAL_MEAL_LOGS -> 0.0;
        };
    }

    @Transactional(readOnly = true)
    public List<Badge> activeBadges() {
        return badgeRepository.findByActiveTrueOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<UserBadge> badgesForUser(User user) {
        return userBadgeRepository.findByUser_IdOrderByAwardedAtDesc(user.getId());
    }

    @Transactional
    public Badge createBadge(String name, String description, String icon, BadgeMetricType metricType,
                             long thresholdValue, boolean active) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tên huy hiệu không được để trống");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Mô tả không được để trống");
        if (metricType == null) throw new IllegalArgumentException("Loại chỉ số không hợp lệ");
        if (thresholdValue <= 0) throw new IllegalArgumentException("Ngưỡng phải lớn hơn 0");
        if (badgeRepository.existsByNameIgnoreCase(name.strip())) throw new IllegalArgumentException("Tên huy hiệu đã tồn tại");
        Badge badge = new Badge();
        badge.setName(name.strip());
        badge.setDescription(description.strip());
        badge.setIcon(icon);
        badge.setMetricType(metricType);
        badge.setThresholdValue(thresholdValue);
        badge.setActive(active);
        Badge saved = badgeRepository.save(badge);
        if (saved.isActive()) backfillAllUsersForBadge(saved);
        return saved;
    }

    @Transactional
    public void setActive(Long badgeId, boolean active) {
        Badge badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy huy hiệu"));
        badge.setActive(active);
        badgeRepository.save(badge);
        if (active) backfillAllUsersForBadge(badge);
    }
}
