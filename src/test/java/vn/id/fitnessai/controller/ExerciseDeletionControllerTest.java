package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.service.*;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ExerciseController.class)
@Import(SecurityConfig.class)
class ExerciseDeletionControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ExerciseService service;
    @MockitoBean CustomUserDetailsService accounts;
    @MockitoBean EntityManagerFactory emf;

    @Test void adminGetsNoContentAfterSuccessfulDeletion() throws Exception {
        when(service.deleteExercise(7)).thenReturn(true);
        mvc.perform(delete("/admin/exercises/7").with(user("a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test void missingExerciseReturns404() throws Exception {
        mvc.perform(delete("/admin/exercises/7").with(user("a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test void openWorkoutConflictReturns409WithBusinessMessage() throws Exception {
        when(service.deleteExercise(7)).thenThrow(new IllegalStateException("Buổi tập chưa kết thúc"));
        mvc.perform(delete("/admin/exercises/7").with(user("a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Buổi tập chưa kết thúc"));
    }

    @Test void databaseConstraintReturnsSafe409InsteadOf500() throws Exception {
        when(service.deleteExercise(7)).thenThrow(new DataIntegrityViolationException("PRIVATE_DATABASE_VALUE"));
        mvc.perform(delete("/admin/exercises/7").with(user("a").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE_DATABASE_VALUE"))));
    }

    @Test void regularUserCannotDelete() throws Exception {
        mvc.perform(delete("/admin/exercises/7").with(user("u")).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void missingCsrfPreventsDeletion() throws Exception {
        mvc.perform(delete("/admin/exercises/7").with(user("a").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
