package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.BadgeCreateRequest;
import vn.id.fitnessai.entity.Badge;
import vn.id.fitnessai.service.BadgeService;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/badges")
public class BadgeAdminController {
    private final BadgeService badgeService;

    @GetMapping
    public List<Map<String, Object>> list() {
        return badgeService.activeBadges().stream().map(b -> Map.<String, Object>of(
                "id", b.getId(), "name", b.getName(), "description", b.getDescription(),
                "icon", b.getIcon() == null ? "" : b.getIcon(),
                "thresholdValue", b.getThresholdValue(), "active", b.isActive()
        )).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody BadgeCreateRequest request) {
        Badge badge = badgeService.createBadge(request.getName(), request.getDescription(),
                request.getIcon(), request.getThresholdValue(), request.isActive());
        return Map.of("id", badge.getId(), "name", badge.getName(),
                "thresholdValue", badge.getThresholdValue());
    }

    @PatchMapping("/{id}/active")
    public Map<String, String> setActive(@PathVariable Long id, @RequestParam boolean active) {
        badgeService.setActive(id, active);
        return Map.of("message", active ? "Đã kích hoạt huy hiệu và cấp bù cho người đủ điều kiện"
                : "Đã ngừng kích hoạt huy hiệu; người đã nhận vẫn giữ nguyên");
    }
}
