package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.repository.UserRepository;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    // Đường dẫn này bắt đầu bằng /admin/ nên Spring Security sẽ tự động yêu cầu quyền ADMIN
    @GetMapping("/admin/dashboard")
    public String showAdminDashboard() {
        // Trả về file giao diện có tên là admin_dashboard.html
        return "admin_dashboard";
    }

    @PostMapping("/admin/users/{id}/lock")
    public String lockUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));

        // Tránh Admin tự khóa mình hoặc khóa Admin khác
        boolean isAdmin = user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            user.setLocked(true);
            userRepository.save(user);
        }
        return "redirect:/admin/dashboard"; // Trở lại trang quản trị
    }

    // Nút bấm mở khóa
    @PostMapping("/admin/users/{id}/unlock")
    public String unlockUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new RuntimeException("User not found"));
        user.setLocked(false);
        userRepository.save(user);
        return "redirect:/admin/dashboard";
    }
}