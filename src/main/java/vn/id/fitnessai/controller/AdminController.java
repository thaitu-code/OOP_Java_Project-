package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.UserRepository;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/admin/dashboard")
    public String showAdminDashboard(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "admin_dashboard";
    }

    @PostMapping("/admin/users/{id}/lock")
    public String lockUser(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

        // Không cho phép khóa chính mình hoặc bất kỳ tài khoản quản trị nào.
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));
        boolean isCurrentUser = authentication != null
                && user.getUsername().equals(authentication.getName());

        if (!isAdmin && !isCurrentUser) {
            user.setLocked(true);
            userRepository.save(user);
        }

        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/users/{id}/unlock")
    public String unlockUser(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));

        // Không cho phép thao tác khóa/mở khóa tài khoản ADMIN.
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));

        if (!isAdmin) {
            user.setLocked(false);
            userRepository.save(user);
        }

        return "redirect:/admin/dashboard";
    }
}