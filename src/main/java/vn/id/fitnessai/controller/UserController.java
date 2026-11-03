package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import vn.id.fitnessai.dto.RegisterRequest;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.VerificationTokenRepository;
import vn.id.fitnessai.service.UserService;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        // Khởi tạo một đối tượng rỗng để form HTML có thể map dữ liệu vào
        model.addAttribute("registerRequest", new RegisterRequest());
        return "register";
    }


    @PostMapping("/register")
    public String processRegister(
            @Valid @ModelAttribute("registerRequest")
            RegisterRequest registerRequest,
            BindingResult bindingResult,
            Model model) {

        // 1. Kiểm tra mật khẩu và xác nhận mật khẩu
        if (registerRequest.getPassword() != null
                && !registerRequest.getPassword()
                .equals(registerRequest.getConfirmPassword())) {
            bindingResult.rejectValue(
                    "confirmPassword",
                    "error.registerRequest",
                    "Mật khẩu xác nhận không khớp!"
            );
        }
        // 2. QUAN TRỌNG: Có lỗi validation thì không đăng ký
        if (bindingResult.hasErrors()) {
            return "register";
        }
        // 3. Chỉ đăng ký khi dữ liệu hợp lệ
        try {
            userService.registerUser(registerRequest);
            return "redirect:/login?registerSuccess=true";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/login")
    public String showLoginForm(){
        return "login";
    }


    @GetMapping("/verify")
    public String verifyUser(
            @RequestParam("token") String token,
            Model model) {

        try {
            userService.verifyEmail(token);
            return "redirect:/login?verifySuccess=true";

        } catch (RuntimeException e) {
            model.addAttribute("message", e.getMessage());
            return "message_page";
        }
    }
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm() {
        return "forgot_password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam("email") String email, Model model) {
        // Không gửi yêu cầu nếu email rỗng hoặc chỉ chứa khoảng trắng.
        if (email == null || email.isBlank()) {
            model.addAttribute("error", "Vui lòng nhập email đã đăng ký.");
            return "forgot_password";
        }

        try {
            userService.processForgotPassword(email.trim());
            model.addAttribute("message", "Đường dẫn đặt lại mật khẩu đã được gửi vào email của bạn.");
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "forgot_password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam("token") String token, Model model) {
        model.addAttribute("token", token);
        return "reset_password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam("token") String token,
                                       @RequestParam("password") String password,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       Model model) {
        model.addAttribute("token", token);

        // Chính sách giống đăng ký: tối thiểu 8 ký tự, có chữ hoa, chữ thường và số.
        if (password == null || !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 8 ký tự, gồm chữ hoa, chữ thường và chữ số.");
            return "reset_password";
        }

        if (confirmPassword == null || !password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp!");
            return "reset_password";
        }

        try {
            userService.resetPassword(token, password);
            return "redirect:/login?resetSuccess=true";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "reset_password";
        }
    }
}
