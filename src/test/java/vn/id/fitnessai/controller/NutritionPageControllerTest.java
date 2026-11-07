package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.service.CustomUserDetailsService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NutritionPageController.class)
@Import(SecurityConfig.class)
class NutritionPageControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private EntityManagerFactory entityManagerFactory;

    @Test
    void anonymousMustSignIn() throws Exception {
        mvc.perform(get("/nutrition")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void userPageRendersAssetsCsrfAndHidesManagement() throws Exception {
        String html = mvc.perform(get("/nutrition").with(user("nutrition-user")))
                .andExpect(status().isOk()).andExpect(view().name("nutrition"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-admin=\"false\"", "data-manage=\"false\"", "data-base=\"/\"",
                        "/js/nutrition.js", "/css/nutrition.css", "name=\"_csrf\"", "Nhật ký ăn uống")
                .doesNotContain("id=\"n-panel-manage\"", "data-tab=\"manage\"", "th:attr=", "content=\"\"");
        savePreview("user.html", html);
    }

    @Test
    void adminLibraryShowsManagementAndStartsInManageMode() throws Exception {
        String html = mvc.perform(get("/admin/nutrition-library").with(user("nutrition-admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("nutrition"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-admin=\"true\"", "data-manage=\"true\"", "id=\"n-panel-manage\"",
                "data-tab=\"manage\"", "Quản lý thư viện dinh dưỡng");
        savePreview("admin.html", html);
    }

    @Test
    void userCannotOpenAdminLibrary() throws Exception {
        mvc.perform(get("/admin/nutrition-library").with(user("nutrition-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void contextPathIsPreserved() throws Exception {
        String html = mvc.perform(get("/fitness/nutrition").contextPath("/fitness").with(user("nutrition-user")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("data-base=\"/fitness/\"", "src=\"/fitness/js/nutrition.js\"",
                "href=\"/fitness/css/nutrition.css\"", "action=\"/fitness/perform_logout\"");
    }

    private void savePreview(String name, String html) throws Exception {
        Path directory = Path.of("target", "nutrition-preview");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name), html, StandardCharsets.UTF_8);
    }
}
