package vn.id.fitnessai.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NutritionPageController {

    @GetMapping("/nutrition")
    public String nutrition(
            Model model,
            Authentication authentication
    ) {
        return page(model, authentication, false);
    }

    @GetMapping("/admin/nutrition-library")
    public String library(
            Model model,
            Authentication authentication
    ) {
        return page(model, authentication, true);
    }

    private String page(
            Model model,
            Authentication authentication,
            boolean manage
    ) {
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                );

        model.addAttribute("isNutritionAdmin", isAdmin);
        model.addAttribute("manageNutritionLibrary", manage);

        return "nutrition";
    }
}