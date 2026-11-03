package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.dto.WorkoutPageResponse;
import vn.id.fitnessai.service.CustomUserDetailsService;
import vn.id.fitnessai.service.WorkoutService;
import vn.id.fitnessai.service.WorkoutTrackingService;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WorkoutController.class,
        properties = "fitness.workout.heartbeat-timeout-seconds=30")
@Import(SecurityConfig.class)
class WorkoutControllerMappingTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private WorkoutService workoutService;
    @MockitoBean private WorkoutTrackingService workoutTrackingService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private EntityManagerFactory entityManagerFactory;

    @Test
    void historyGetIsMappedAndPassesPaginationToService() throws Exception {
        when(workoutService.getWorkouts("mapping-test", 0, 1, null))
                .thenReturn(new WorkoutPageResponse(List.of(), 0, 1, 0, 0));

        mvc.perform(get("/workouts").param("page", "0").param("size", "1")
                        .with(user("mapping-test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(workoutService).getWorkouts("mapping-test", 0, 1, null);
    }

    @Test
    void noCurrentWorkoutIsController404WithEmptyBody() throws Exception {
        when(workoutService.getCurrentWorkout("mapping-test")).thenReturn(Optional.empty());

        mvc.perform(get("/workouts/current").with(user("mapping-test")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        verify(workoutService).getCurrentWorkout("mapping-test");
    }

    @Test
    void combinedSnapshotHasItsOwnRouteAndEmpty404() throws Exception {
        when(workoutService.getCurrentWorkoutState("mapping-test")).thenReturn(Optional.empty());
        mvc.perform(get("/workouts/current/state").with(user("mapping-test")))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        verify(workoutService).getCurrentWorkoutState("mapping-test");
    }
}
