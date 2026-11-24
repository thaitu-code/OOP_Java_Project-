package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.UserProfileRepository;

/** Small view projection; account entities never enter the shared template model. */
@ControllerAdvice(assignableTypes = {HomeController.class, WorkoutPageController.class, NutritionPageController.class,
        UserProfileController.class, AiAssistantController.class, UserController.class, AdminController.class, BadgeAdminPageController.class})
@RequiredArgsConstructor
public class UiIdentityAdvice {
    private final ObjectProvider<UserRepository> users;
    private final ObjectProvider<UserProfileRepository> profiles;
    public record Identity(boolean authenticated, boolean admin, String displayName, String email, String initials) {}

    @ModelAttribute("uiIdentity")
    @Transactional(readOnly = true)
    public Identity identity(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken)
            return new Identity(false, false, "", "", "F");
        var userRepository = users.getIfAvailable();
        var profileRepository = profiles.getIfAvailable();
        var user = userRepository == null ? null : userRepository.findByUsername(authentication.getName()).orElse(null);
        String name = profileRepository == null ? authentication.getName() : profileRepository.findByUserUsername(authentication.getName())
                .map(p -> p.getDisplayName()).filter(n -> n != null && !n.isBlank()).orElse(authentication.getName());
        String[] words = name.trim().split("\\s+");
        String initials = (words[0].substring(0, 1) + (words.length > 1 ? words[words.length - 1].substring(0, 1) : "")).toUpperCase(java.util.Locale.ROOT);
        return new Identity(true, authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")), name, user == null ? authentication.getName() : user.getEmail(), initials);
    }
}
