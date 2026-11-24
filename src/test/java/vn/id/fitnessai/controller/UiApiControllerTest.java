package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.service.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={DashboardController.class,WorkoutSelectionController.class}) @Import(SecurityConfig.class)
class UiApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DashboardService dashboard;
    @MockitoBean WorkoutSelectionService selections;
    @MockitoBean CustomUserDetailsService security;
    @MockitoBean EntityManagerFactory entityManagerFactory;
    private static final String BODY="{\"requestId\":\"550e8400-e29b-41d4-a716-446655440000\",\"items\":[{\"exerciseId\":1}]}";
    @Test void authenticationAndCsrfAreRequired()throws Exception{
        mvc.perform(get("/api/dashboard/summary")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/workouts/from-selection").with(user("u")).contentType("application/json").content(BODY)).andExpect(status().isForbidden());verifyNoInteractions(selections);
    }
    @Test void rejectsEmptyOrInvalidSelections()throws Exception{
        for(String body:new String[]{"{\"items\":[]}",BODY.replace("\"exerciseId\":1","\"exerciseId\":-1"),BODY.replace("\"items\":[{\"exerciseId\":1}]","\"items\":[null]")})
            mvc.perform(post("/workouts/from-selection").with(user("u")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(selections);
    }
    @Test void conflictsAndLockedAccountsHaveExplicitStatuses()throws Exception{
        when(selections.create(eq("u"),any())).thenThrow(new IllegalStateException("Đã thay đổi"));
        mvc.perform(post("/workouts/from-selection").with(user("u")).with(csrf()).contentType("application/json").content(BODY)).andExpect(status().isConflict());
        when(dashboard.summary("u")).thenThrow(new SecurityException("Tài khoản đã khóa"));
        mvc.perform(get("/api/dashboard/summary").with(user("u"))).andExpect(status().isForbidden());
    }
}
