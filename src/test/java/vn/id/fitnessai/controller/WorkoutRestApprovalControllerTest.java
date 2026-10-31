package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.WorkoutDetailRepository;
import vn.id.fitnessai.repository.WorkoutRepository;
import vn.id.fitnessai.service.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WorkoutController.class,
        properties = "fitness.workout.heartbeat-timeout-seconds=30")
@Import({SecurityConfig.class, WorkoutTrackingService.class})
class WorkoutRestApprovalControllerTest {
    private static final String USERNAME = "workout-http-test";
    private static final String URL = "/workouts/10/details/20/rest-duration";
    private static final OffsetDateTime START =
            OffsetDateTime.parse("2026-11-01T16:00:00+07:00");

    @Autowired private MockMvc mvc;
    @MockitoBean private WorkoutService workoutService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private WorkoutRepository workoutRepository;
    @MockitoBean private WorkoutDetailRepository detailRepository;
    @MockitoBean private CheckInStreakService checkInStreakService;
    @MockitoBean private BadgeService badgeService;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private EntityManagerFactory entityManagerFactory;

    private User account;
    private Workout workout;
    private WorkoutDetail detail;

    @BeforeEach
    void setUp() {
        account = new User();
        account.setId(1L);
        account.setUsername(USERNAME);
        account.setEnabled(true);
        workout = new Workout(account, START);
        ReflectionTestUtils.setField(workout, "workoutId", 10);
        Exercise exercise = new Exercise("Bài kiểm thử HTTP", new BigDecimal("6.0"),
                new BigDecimal("1.0"), new ExerciseCategory("Nhóm kiểm thử"));
        detail = new WorkoutDetail(workout, exercise);
        ReflectionTestUtils.setField(detail, "detailId", 20);

        when(userRepository.findWorkoutAccessForUpdate(USERNAME)).thenReturn(Optional.of(new UserRepository.WorkoutAccess() {
            public Long getId() { return account.getId(); }
            public boolean getEnabled() { return account.isEnabled(); }
            public boolean getLocked() { return account.isLocked(); }
        }));
        when(workoutRepository.findByIdForUpdate(10)).thenReturn(Optional.of(workout));
        when(detailRepository.findByDetailIdAndWorkout_workoutId(20, 10))
                .thenReturn(Optional.of(detail));
        when(detailRepository.saveAndFlush(detail)).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void approvalDoesNotStartWorkoutOrAwardCheckIn() {
        verifyNoInteractions(workoutService, checkInStreakService, badgeService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void authenticatedOwnerCanApproveRest(String role) throws Exception {
        mvc.perform(patch(URL).with(user(USERNAME).roles(role)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":120}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.detailId").value(20))
                .andExpect(jsonPath("$.workoutId").value(10))
                .andExpect(jsonPath("$.approvedRestSecondsSnapshot").value(120))
                .andExpect(jsonPath("$.timerToken").doesNotExist());

        verify(detailRepository).saveAndFlush(detail);
        assertEquals(Integer.valueOf(120), detail.getApprovedRestSecondsSnapshot());
        assertEquals(WorkoutDetailStatus.NOT_STARTED, detail.getStatus());
        assertNull(detail.getStartedAt());
        assertNull(detail.getTimerToken());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 179, 300})
    void acceptsBoundsAndRestBelowThreeMinutes(int seconds) throws Exception {
        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restSeconds\":" + seconds + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedRestSecondsSnapshot").value(seconds));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"restSeconds\":null}", "{\"restSeconds\":0}",
            "{\"restSeconds\":-1}", "{\"restSeconds\":301}",
            "{\"restSeconds\":\"abc\"}", "null", "{"
    })
    void invalidBodyReturns400BeforeRepositoryAccess(String body) throws Exception {
        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @Test
    void anonymousRequestWithCsrfRedirectsToLogin() throws Exception {
        mvc.perform(patch(URL).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @Test
    void missingCsrfReturns403() throws Exception {
        mvc.perform(patch(URL).with(user(USERNAME))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @Test
    void invalidCsrfReturns403() throws Exception {
        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().useInvalidToken().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void otherOwnerReturns403IncludingAdmin(String role) throws Exception {
        User otherOwner = new User();
        otherOwner.setId(2L);
        workout.setUser(otherOwner);

        mvc.perform(patch(URL).with(user(USERNAME).roles(role)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Bạn không có quyền thay đổi thời lượng nghỉ trong buổi tập này"));
        verifyNoInteractions(detailRepository);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true"})
    void unverifiedOrLockedAccountReturns403(boolean enabled, boolean locked) throws Exception {
        account.setEnabled(enabled);
        account.setLocked(locked);

        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(workoutRepository, detailRepository);
    }

    @Test
    void missingWorkoutReturns404() throws Exception {
        when(workoutRepository.findByIdForUpdate(10)).thenReturn(Optional.empty());

        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        verifyNoInteractions(detailRepository);
    }

    @Test
    void missingDetailInWorkoutReturns404() throws Exception {
        when(detailRepository.findByDetailIdAndWorkout_workoutId(20, 10)).thenReturn(Optional.empty());

        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isNotFound()).andExpect(content().string(""));
        verify(detailRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = WorkoutStatus.class, names = {"COMPLETED", "CANCELLED"})
    void closedWorkoutReturns409(WorkoutStatus status) throws Exception {
        workout.setStatus(status);

        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Buổi tập đã kết thúc hoặc hủy"));
        verifyNoInteractions(detailRepository);
    }

    @Test
    void completedDetailReturns409AndPreservesHistory() throws Exception {
        detail.start(START);
        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(30), Duration.ofSeconds(30));
        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(60), Duration.ofSeconds(30));
        assertTrue(detail.complete(detail.getTimerToken(), START.plusSeconds(60),
                Duration.ofSeconds(30), new BigDecimal("65.0"), null, null, null));
        OffsetDateTime completedAt = detail.getCompletedAt();

        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isConflict());

        assertEquals(completedAt, detail.getCompletedAt());
        assertNull(detail.getApprovedRestSecondsSnapshot());
        verify(detailRepository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/workouts/0/details/20/rest-duration",
            "/workouts/10/details/-1/rest-duration",
            "/workouts/invalid/details/20/rest-duration"
    })
    void invalidPathIdReturns400(String path) throws Exception {
        mvc.perform(patch(path).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":60}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @Test
    void approvalDuringRestPreservesCurrentIntervalAndTimer() throws Exception {
        detail.start(START);
        detail.approveRestDuration(60);
        detail.startRest(detail.getTimerToken(), START.plusSeconds(10), Duration.ofSeconds(30));
        UUID token = detail.getTimerToken();

        mvc.perform(patch(URL).with(user(USERNAME)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restSeconds\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedRestSecondsSnapshot").value(120));

        assertEquals(WorkoutDetailStatus.RESTING, detail.getStatus());
        assertEquals(START.plusSeconds(70), detail.getRestEndsAt());
        assertEquals(token, detail.getTimerToken());
        assertEquals(10_000L, detail.getActiveDurationMillis());
        assertEquals(0L, detail.getRestDurationMillis());
    }
}
