package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.service.BadgeService;
import vn.id.fitnessai.service.CheckInStreakService;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class CheckInController {
    private final CheckInStreakService checkInStreakService;
    private final BadgeService badgeService;
    private final UserRepository userRepository;

    @GetMapping("/check-ins/summary")
    public CheckInStreakService.StreakSummary summary(Authentication authentication) {
        return checkInStreakService.getSummary(authentication.getName());
    }

    @GetMapping("/badges")
    public List<Map<String, Object>> activeBadges() {
        return badgeService.activeBadges().stream().map(b -> Map.<String, Object>of(
                "id", b.getId(), "name", b.getName(), "description", b.getDescription(),
                "icon", b.getIcon() == null ? "" : b.getIcon(), "metricType", b.getMetricType(),
                "thresholdValue", b.getThresholdValue()
        )).toList();
    }

    @GetMapping("/badges/mine")
    public List<Map<String, Object>> myBadges(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
        return badgeService.badgesForUser(user).stream().map(ub -> Map.<String, Object>of(
                "badgeId", ub.getBadge().getId(),
                "name", ub.getBadge().getName(),
                "description", ub.getBadge().getDescription(),
                "icon", ub.getBadge().getIcon() == null ? "" : ub.getBadge().getIcon(),
                "awardedAt", ub.getAwardedAt(),
                "metricValueAtAward", ub.getMetricValueAtAward()
        )).toList();
    }
}
