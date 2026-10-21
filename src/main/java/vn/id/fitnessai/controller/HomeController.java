package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.id.fitnessai.dto.UserProfileResponse;
import vn.id.fitnessai.service.UserProfileService;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserProfileService userProfileService;

    @GetMapping({"/", "/home"})
    public String homePage(Model model, Principal principal) {
        // Nếu người dùng đã đăng nhập, lấy thông tin profile đẩy sang view home.html
        if (principal != null) {
            UserProfileResponse profile = userProfileService.getUserProfile(principal.getName());
            model.addAttribute("profile", profile);
        }
        return "home";
    }
}