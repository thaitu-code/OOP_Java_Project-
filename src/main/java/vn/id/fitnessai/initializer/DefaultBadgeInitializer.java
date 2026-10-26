package vn.id.fitnessai.initializer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vn.id.fitnessai.enums.BadgeMetricType;
import vn.id.fitnessai.repository.BadgeRepository;
import vn.id.fitnessai.service.BadgeService;

@Component
@RequiredArgsConstructor
public class DefaultBadgeInitializer implements CommandLineRunner {
    private final BadgeRepository badgeRepository;
    private final BadgeService badgeService;

    @Override
    public void run(String... args) {
        createIfMissing("Khởi đầu", "Check-in 3 ngày liên tiếp", "🌱", 3);
        createIfMissing("Kỷ luật", "Check-in 7 ngày liên tiếp", "🔥", 7);
        createIfMissing("Bền bỉ", "Check-in 14 ngày liên tiếp", "💪", 14);
        createIfMissing("Chinh phục tháng", "Check-in 30 ngày liên tiếp", "🏆", 30);
        createIfMissing("Không thể ngăn cản", "Check-in 60 ngày liên tiếp", "⚡", 60);
        createIfMissing("Huyền thoại", "Check-in 100 ngày liên tiếp", "👑", 100);
    }

    private void createIfMissing(String name, String description, String icon, long threshold) {
        if (!badgeRepository.existsByNameIgnoreCase(name)) {
            badgeService.createBadge(name, description, icon, BadgeMetricType.STREAK_DAYS, threshold, true);
        }
    }
}
