package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.id.fitnessai.service.UserProfileService;

@Controller
@RequiredArgsConstructor
public class WorkoutPageController {
    private final UserProfileService userProfileService;

    @Value("${fitness.workout.heartbeat-interval-seconds:10}")
    private long heartbeatIntervalSeconds;

    @Value("${fitness.workout.heartbeat-timeout-seconds:30}")
    private long heartbeatTimeoutSeconds;

    @GetMapping("/workout")
    public String workout(Model model, Authentication authentication) {
        return page(model, authentication, false);
    }

    @GetMapping("/admin/workout-library")
    public String library(Model model, Authentication authentication) {
        return page(model, authentication, true);
    }

    private String page(Model model, Authentication authentication, boolean manage) {
        model.addAttribute("profile", userProfileService.getUserProfile(authentication.getName()));
        model.addAttribute("isWorkoutAdmin", authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
        model.addAttribute("manageLibrary", manage);
        model.addAttribute("heartbeatIntervalSeconds", heartbeatIntervalSeconds);
        model.addAttribute("heartbeatTimeoutSeconds", heartbeatTimeoutSeconds);
        return "workout";
    }
}
