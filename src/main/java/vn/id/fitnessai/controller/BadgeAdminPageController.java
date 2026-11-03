package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.BadgeCreateRequest;
import vn.id.fitnessai.repository.BadgeRepository;
import vn.id.fitnessai.service.BadgeService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/badges/page")
public class BadgeAdminPageController {
    private final BadgeRepository badgeRepository;
    private final BadgeService badgeService;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("badges", badgeRepository.findAll());
        model.addAttribute("badgeForm", new BadgeCreateRequest());
        return "admin_badges";
    }

    @PostMapping
    public String create(@ModelAttribute("badgeForm") BadgeCreateRequest form, Model model) {
        try {
            badgeService.createBadge(form.getName(), form.getDescription(), form.getIcon(),
                     form.getThresholdValue(), form.isActive());
            return "redirect:/admin/badges/page?created=true";
        } catch (RuntimeException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            model.addAttribute("badges", badgeRepository.findAll());
            return "admin_badges";
        }
    }

    @PostMapping("/{id}/active")
    public String active(@PathVariable Long id, @RequestParam boolean active) {
        badgeService.setActive(id, active);
        return "redirect:/admin/badges/page";
    }
}
