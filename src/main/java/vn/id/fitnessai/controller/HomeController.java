package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.id.fitnessai.dto.UserProfileResponse;
import vn.id.fitnessai.service.UserProfileService;
import vn.id.fitnessai.service.CheckInStreakService;
import vn.id.fitnessai.service.BadgeService;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.enums.BadgeMetricType;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserProfileService userProfileService;
    private final CheckInStreakService checkInStreakService;
    private final BadgeService badgeService;
    private final UserRepository userRepository;

    @GetMapping({"/", "/home"})
    public String homePage(Model model, Principal principal) {
        // Nếu người dùng đã đăng nhập, lấy thông tin profile đẩy sang view home.html
        if (principal != null) {
            UserProfileResponse profile = userProfileService.getUserProfile(principal.getName());
            model.addAttribute("profile", profile);

            User user = userRepository.findByUsername(principal.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
            CheckInStreakService.StreakSummary streak = checkInStreakService.getSummary(principal.getName());
            var activeBadges = badgeService.activeBadges();
            var earnedBadges = badgeService.badgesForUser(user);
            var earnedBadgeIds = earnedBadges.stream()
                    .map(userBadge -> userBadge.getBadge().getId())
                    .collect(java.util.stream.Collectors.toSet());

            model.addAttribute("streakSummary", streak);
            model.addAttribute("activeBadges", activeBadges);
            model.addAttribute("nextStreakBadge", activeBadges.stream()
                    .filter(badge -> badge.getMetricType() == BadgeMetricType.STREAK_DAYS)
                    .filter(badge -> badge.getThresholdValue() > streak.currentStreak())
                    .min(java.util.Comparator.comparingLong(vn.id.fitnessai.entity.Badge::getThresholdValue))
                    .orElse(null));
            model.addAttribute("earnedBadges", earnedBadges);
            model.addAttribute("earnedBadgeIds", earnedBadgeIds);
            model.addAttribute("streakBadgeMetric", BadgeMetricType.STREAK_DAYS);
        }
        return "home";
    }
}