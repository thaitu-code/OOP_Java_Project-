package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import vn.id.fitnessai.dto.RegisterRequest;
import vn.id.fitnessai.service.UserService;

@Controller
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        // Khởi tạo một đối tượng rỗng để form HTML có thể map dữ liệu vào
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@ModelAttribute("registerRequest") RegisterRequest registerRequest, Model model) {
        try {
            // Gọi service để lưu user (có check trùng lặp)
            userService.registerUser(registerRequest);
            // Đăng ký xong thì chuyển về trang login kèm thông báo thành công
            return "redirect:/login?registerSuccess=true";
        } catch (RuntimeException e) {
            // Nếu có lỗi (ví dụ: Tên đăng nhập đã tồn tại), quay lại trang đăng ký và báo lỗi
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }
    @GetMapping("/login")
    public String showLoginForm(){
        return "login";
    }

    @GetMapping("/home")
    public String showHomePage(){
        return "home";
    }
}
