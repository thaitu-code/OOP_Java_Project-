package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.id.fitnessai.dto.ProfileUpdateRequest;
import vn.id.fitnessai.dto.UserProfileResponse;
import vn.id.fitnessai.enums.ActivityLevel;
import vn.id.fitnessai.enums.FitnessGoal;
import vn.id.fitnessai.enums.Gender;
import vn.id.fitnessai.service.UserProfileService;

import java.security.Principal;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public String showProfilePage(Model model, Principal principal) {
        UserProfileResponse profile = userProfileService.getUserProfile(principal.getName());
        model.addAttribute("profile", profile);

        // Chuẩn bị form DTO từ dữ liệu profile hiện tại
        ProfileUpdateRequest form = new ProfileUpdateRequest();
        if (profile.getDisplayName() != null) {
            form.setDisplayName(profile.getDisplayName());
            form.setGender(profile.getGender());
            form.setBirthDate(profile.getBirthDate());
            form.setHeightCm(profile.getHeightCm());
            form.setWeightKg(profile.getWeightKg());
            form.setActivityLevel(profile.getActivityLevel());
            form.setGoal(profile.getGoal());
        }
        model.addAttribute("profileForm", form);

        populateEnums(model);
        return "profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @Valid @ModelAttribute("profileForm") ProfileUpdateRequest profileForm,
            BindingResult bindingResult,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            UserProfileResponse profile = userProfileService.getUserProfile(principal.getName());
            model.addAttribute("profile", profile);
            populateEnums(model);
            return "profile";
        }

        try {
            userProfileService.updateUserProfile(principal.getName(), profileForm);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hồ sơ và tính toán chỉ số thành công!");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            UserProfileResponse profile = userProfileService.getUserProfile(principal.getName());
            model.addAttribute("profile", profile);
            populateEnums(model);
            return "profile";
        }

        return "redirect:/profile";
    }

    private void populateEnums(Model model) {
        model.addAttribute("genders", Gender.values());
        model.addAttribute("activityLevels", ActivityLevel.values());
        model.addAttribute("goals", FitnessGoal.values());
    }
}