package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;

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
            double metric = metricValue(user);
            if (metric >= badge.getThresholdValue()) {
                userBadgeRepository.save(new UserBadge(user, badge, metric));
            }
        }
    }

    @Transactional
    public void backfillAllUsersForBadge(Badge badge) {
        for (User user : workoutUsersAndAllUsers()) {
            if (badge.isActive() && !userBadgeRepository.existsByUser_IdAndBadge_Id(user.getId(), badge.getId())) {
                double metric = metricValue(user);
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
    public double metricValue(User user) {
        return userStreakRepository
                .findByUser_Id(user.getId())
                .map(UserStreak::getHighestStreak)
                .orElse(0);
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
    public Badge createBadge(String name, String description, String icon,
                             long thresholdValue, boolean active) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Tên huy hiệu không được để trống");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Mô tả không được để trống");
        if (thresholdValue <= 0) throw new IllegalArgumentException("Ngưỡng phải lớn hơn 0");
        if (badgeRepository.existsByNameIgnoreCase(name.strip())) throw new IllegalArgumentException("Tên huy hiệu đã tồn tại");
        Badge badge = new Badge();
        badge.setName(name.strip());
        badge.setDescription(description.strip());
        badge.setIcon(icon);
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
