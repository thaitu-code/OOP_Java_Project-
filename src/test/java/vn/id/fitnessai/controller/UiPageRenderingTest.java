package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.dto.UserProfileResponse;
import vn.id.fitnessai.entity.User;
import vn.id.fitnessai.enums.*;
import vn.id.fitnessai.repository.*;
import vn.id.fitnessai.service.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers={HomeController.class, UserProfileController.class, AiAssistantController.class, UserController.class,AdminController.class,BadgeAdminPageController.class})
@Import(SecurityConfig.class)
class UiPageRenderingTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserProfileService profiles;
    @MockitoBean CheckInStreakService streaks;
    @MockitoBean BadgeService badges;
    @MockitoBean UserRepository users;
    @MockitoBean VerificationTokenRepository tokens;
    @MockitoBean BadgeRepository badgeRepository;
    @MockitoBean AiAssistantService ai;
    @MockitoBean AiAssistantController.Api aiApi;
    @MockitoBean UserService userService;
    @MockitoBean CustomUserDetailsService security;
    @MockitoBean EntityManagerFactory entityManagerFactory;

    @BeforeEach void data(){
        var u=new User();u.setId(1L);u.setUsername("preview-user");u.setEmail("preview@example.test");u.setEnabled(true);
        when(users.findByUsername("preview-user")).thenReturn(Optional.of(u));
        when(profiles.getUserProfile("preview-user")).thenReturn(UserProfileResponse.builder().version(0L).displayName("Người dùng kiểm thử")
                .birthDate(LocalDate.of(2000,1,1)).gender(Gender.MALE).heightCm(new BigDecimal("170")).weightKg(new BigDecimal("65"))
                .activityLevel(ActivityLevel.MODERATE).goal(FitnessGoal.MAINTAIN).age(26).calculatedBmi(new BigDecimal("22.5"))
                .calculatedBmr(new BigDecimal("1600")).calculatedTdee(new BigDecimal("2400")).profileCalculatedAt(LocalDateTime.of(2026,11,13,10,0)).build());
        when(streaks.getSummary("preview-user")).thenReturn(new CheckInStreakService.StreakSummary(3,7,0,false,LocalDate.of(2026,11,13)));
    }
    private String render(String path,String name,boolean auth)throws Exception{
        var request=get(path);if(auth)request.with(user("preview-user"));
        String html=mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Path dir=Path.of("target/ui-preview");Files.createDirectories(dir);Files.writeString(dir.resolve(name+".html"),html);
        assertThat(html).doesNotContain("th:href=", "th:text=", "th:replace=", "cdn.tailwindcss.com");return html;
    }
    @Test void dashboardRendersRealBindingsAndAvatarNavigation()throws Exception{
        String html=render("/home","home",true);assertThat(html).contains("dashboard-app","app-account","Nhịp sống 7 ngày","3 ngày","/js/dashboard.js").doesNotContain("1920","500 kcal","data-tab=\"manage\"");
    }
    @Test void profileStartsReadOnlyAndPreservesVersionAndDomainFields()throws Exception{
        String html=render("/profile","profile",true);assertThat(html).contains("profile-fields\" disabled", "name=\"expectedVersion\"", "birthDate", "value=\"2000-01-01\"", "INCREASE_HEIGHT", "65", "profile-edit");
    }
    @Test void chatHasOneComposerAndWorkingSuggestionsWithoutUnsupportedControls()throws Exception{
        String html=render("/ai","ai",true);assertThat(html).contains("id=\"chat-text\"","id=\"send\"","data-prompt=","id=\"proposals\"").doesNotContain("ai-execute","type=\"file\"");
    }
    @Test void authFamilyKeepsExistingLoginFrame()throws Exception{
        assertThat(render("/login","login",false)).contains("has-intro", "data-toggle-panel", "fitness-auth", "/css/app-ui.css");
        render("/register","register",false);render("/forgot-password","forgot",false);render("/reset-password?token=preview-token","reset",false);
    }
    @Test void anonymousDashboardHasNoPersonalData()throws Exception{
        assertThat(render("/home","guest",false)).doesNotContain("id=\"dashboard-app\"","id=\"app-account\"").contains("Tạo tài khoản");
    }
    @Test void adminPagesRetainTheirFormsAndSharedMenu()throws Exception{
        for(String path:List.of("/admin/dashboard","/admin/badges/page")){
            String html=mvc.perform(get(path).with(user("preview-user").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            Path dir=Path.of("target/ui-preview");Files.createDirectories(dir);Files.writeString(dir.resolve(path.endsWith("page")?"badges.html":"admin.html"),html);
            assertThat(html).contains("app-account", "Quản trị hệ thống", "/perform_logout");
            mvc.perform(get(path).with(user("preview-user"))).andExpect(status().isForbidden());
        }
    }
    @Test void profileDateInputBindsIsoDateAndKeepsVersion()throws Exception{
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/profile/update").with(user("preview-user"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .param("displayName","Test").param("birthDate","2000-01-01").param("gender","MALE").param("heightCm","170")
                .param("weightKg","65").param("activityLevel","MODERATE").param("goal","MAINTAIN").param("expectedVersion","0"))
                .andExpect(status().is3xxRedirection());
        verify(profiles).updateUserProfile(eq("preview-user"),argThat(r->r.getBirthDate().equals(LocalDate.of(2000,1,1))&&r.getExpectedVersion()==0L));
    }
}
