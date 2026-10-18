package vn.id.fitnessai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    // Đường dẫn này bắt đầu bằng /admin/ nên Spring Security sẽ tự động yêu cầu quyền ADMIN
    @GetMapping("/admin/dashboard")
    public String showAdminDashboard() {
        // Trả về file giao diện có tên là admin_dashboard.html
        return "admin_dashboard";
    }
}