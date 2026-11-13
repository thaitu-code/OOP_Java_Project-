package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.dto.UserProfileResponse;
import vn.id.fitnessai.service.CustomUserDetailsService;
import vn.id.fitnessai.service.UserProfileService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WorkoutPageController.class)
@Import(SecurityConfig.class)
class WorkoutPageControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserProfileService profileService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private EntityManagerFactory entityManagerFactory;

    @Test
    void anonymousMustSignIn() throws Exception {
        mvc.perform(get("/workout")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void userPageRendersCsrfAndHidesAdministration() throws Exception {
        when(profileService.getUserProfile("ui-user")).thenReturn(
                UserProfileResponse.builder().weightKg(new BigDecimal("60")).build());
        String html = mvc.perform(get("/workout").with(user("ui-user")))
                .andExpect(status().isOk()).andExpect(view().name("workout"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-admin=\"false\"", "data-base=\"/\"", "/js/workout.js",
                        "name=\"_csrf\"", "Tạo buổi tập")
                .doesNotContain("id=\"panel-manage\"", "data-tab=\"manage\"", "th:attr=", "content=\"\"");
        savePreview("user.html", html);
    }

    @Test
    void adminPageRendersManagementAndProfileReminder() throws Exception {
        String html = mvc.perform(get("/admin/workout-library").with(user("ui-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("workout"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-admin=\"true\"", "data-manage=\"true\"", "id=\"panel-manage\"",
                "Cập nhật cân nặng", "data-heartbeat=\"10\"", "data-timeout=\"30\"");
        savePreview("admin.html", html);
    }

    @Test
    void userCannotOpenAdminPage() throws Exception {
        mvc.perform(get("/admin/workout-library").with(user("ui-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void contextPathIsPreservedInApiAndAssetUrls() throws Exception {
        String html = mvc.perform(get("/fitness/workout").contextPath("/fitness").with(user("ui-user")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-base=\"/fitness/\"", "src=\"/fitness/js/workout.js\"",
                "href=\"/fitness/css/workout.css\"");
    }

    private void savePreview(String name, String html) throws Exception {
        Path directory = Path.of("target", "workout-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name), html, StandardCharsets.UTF_8);
    }
}
