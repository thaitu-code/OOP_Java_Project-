package vn.id.fitnessai.service;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.id.fitnessai.dto.WorkoutRestApprovalRequest;
import vn.id.fitnessai.dto.WorkoutRestApprovalResponse;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.UserRepository;
import vn.id.fitnessai.repository.WorkoutDetailRepository;
import vn.id.fitnessai.repository.WorkoutRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutRestApprovalServiceTest {
    private static final String USERNAME = "workout-test";
    private static final int WORKOUT_ID = 10;
    private static final int DETAIL_ID = 20;
    private static final OffsetDateTime START =
            OffsetDateTime.parse("2026-11-01T16:00:00+07:00");
    private static ValidatorFactory validatorFactory;

    @Mock private WorkoutRepository workoutRepository;
    @Mock private WorkoutDetailRepository detailRepository;
    @Mock private UserRepository userRepository;
    @Mock private CheckInStreakService checkInStreakService;
    @Mock private BadgeService badgeService;

    private WorkoutTrackingService service;
    private User user;
    private Workout workout;
    private WorkoutDetail detail;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        service = new WorkoutTrackingService(workoutRepository, detailRepository,
                userRepository, checkInStreakService, badgeService, validatorFactory.getValidator());
        user = new User();
        user.setId(1L);
        user.setUsername(USERNAME);
        user.setEnabled(true);
        workout = new Workout(user, START);
        ReflectionTestUtils.setField(workout, "workoutId", WORKOUT_ID);
        Exercise exercise = new Exercise("Bài kiểm thử", new BigDecimal("6.0"),
                new BigDecimal("1.0"), new ExerciseCategory("Nhóm kiểm thử"));
        detail = new WorkoutDetail(workout, exercise);
        ReflectionTestUtils.setField(detail, "detailId", DETAIL_ID);
    }

    private UserRepository.WorkoutAccess workoutAccess() {
        return new UserRepository.WorkoutAccess() {
            public Long getId() { return user.getId(); }
            public boolean getEnabled() { return user.isEnabled(); }
            public boolean getLocked() { return user.isLocked(); }
        };
    }

    @AfterEach
    void approvalNeverAwardsCheckInOrBadges() {
        verifyNoInteractions(checkInStreakService, badgeService);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 60, 179, 300})
    void savesApprovalWithUserThenWorkoutLockWithoutStartingTimer(int seconds) {
        stubDetail();
        when(detailRepository.saveAndFlush(detail)).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutRestApprovalResponse response = approve(seconds).orElseThrow();

        assertEquals(Integer.valueOf(DETAIL_ID), response.getDetailId());
        assertEquals(Integer.valueOf(WORKOUT_ID), response.getWorkoutId());
        assertEquals(Integer.valueOf(seconds), response.getApprovedRestSecondsSnapshot());
        assertEquals(Integer.valueOf(seconds), detail.getApprovedRestSecondsSnapshot());
        assertEquals(WorkoutDetailStatus.NOT_STARTED, detail.getStatus());
        assertNull(detail.getStartedAt());
        assertNull(detail.getTimerToken());
        assertEquals(0L, detail.getActiveDurationMillis());
        assertEquals(BigDecimal.ZERO, detail.getCaloriesBurned());

        InOrder order = inOrder(userRepository, workoutRepository, detailRepository);
        order.verify(userRepository).findWorkoutAccessForUpdate(USERNAME);
        order.verify(workoutRepository).findByIdForUpdate(WORKOUT_ID);
        order.verify(detailRepository).findByDetailIdAndWorkout_workoutId(DETAIL_ID, WORKOUT_ID);
        order.verify(detailRepository).saveAndFlush(detail);
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0, 301, Integer.MAX_VALUE})
    void invalidRestSecondsAreRejectedBeforeDatabaseAccess(Integer seconds) {
        assertThrows(IllegalArgumentException.class, () -> approve(seconds));

        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
        assertNull(detail.getApprovedRestSecondsSnapshot());
    }

    @Test
    void nullRequestIsRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.approveWorkoutDetailRestDuration(USERNAME, WORKOUT_ID, DETAIL_ID, null));
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    @ParameterizedTest
    @MethodSource("invalidIdentifiers")
    void invalidIdentifiersAreRejectedBeforeDatabaseAccess(String username, Integer workoutId,
                                                          Integer detailId) {
        assertThrows(IllegalArgumentException.class,
                () -> service.approveWorkoutDetailRestDuration(username, workoutId, detailId, request(60)));
        verifyNoInteractions(userRepository, workoutRepository, detailRepository);
    }

    static Stream<Arguments> invalidIdentifiers() {
        return Stream.of(
                Arguments.of(null, WORKOUT_ID, DETAIL_ID),
                Arguments.of("", WORKOUT_ID, DETAIL_ID),
                Arguments.of("  ", WORKOUT_ID, DETAIL_ID),
                Arguments.of(USERNAME, null, DETAIL_ID),
                Arguments.of(USERNAME, 0, DETAIL_ID),
                Arguments.of(USERNAME, -1, DETAIL_ID),
                Arguments.of(USERNAME, WORKOUT_ID, null),
                Arguments.of(USERNAME, WORKOUT_ID, 0),
                Arguments.of(USERNAME, WORKOUT_ID, -1)
        );
    }

    @Test
    void missingUserIsRejected() {
        when(userRepository.findWorkoutAccessForUpdate(USERNAME)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> approve(60));

        verifyNoInteractions(workoutRepository, detailRepository);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true"})
    void unverifiedOrLockedAccountIsRejected(boolean enabled, boolean locked) {
        user.setEnabled(enabled);
        user.setLocked(locked);
        when(userRepository.findWorkoutAccessForUpdate(USERNAME)).thenReturn(Optional.of(workoutAccess()));

        assertThrows(AccessDeniedException.class, () -> approve(60));

        verifyNoInteractions(workoutRepository, detailRepository);
    }

    @Test
    void missingWorkoutReturnsEmpty() {
        when(userRepository.findWorkoutAccessForUpdate(USERNAME)).thenReturn(Optional.of(workoutAccess()));
        when(workoutRepository.findByIdForUpdate(WORKOUT_ID)).thenReturn(Optional.empty());

        assertTrue(approve(60).isEmpty());

        verifyNoInteractions(detailRepository);
    }

    @Test
    void otherOwnerCannotChangeApproval() {
        User otherOwner = new User();
        otherOwner.setId(2L);
        workout.setUser(otherOwner);
        stubWorkout();

        assertThrows(AccessDeniedException.class, () -> approve(60));

        verifyNoInteractions(detailRepository);
        assertNull(detail.getApprovedRestSecondsSnapshot());
    }

    @ParameterizedTest
    @EnumSource(value = WorkoutStatus.class, names = {"COMPLETED", "CANCELLED"})
    void closedWorkoutIsRejectedBeforeLoadingDetail(WorkoutStatus status) {
        workout.setStatus(status);
        stubWorkout();

        assertThrows(IllegalStateException.class, () -> approve(60));

        verifyNoInteractions(detailRepository);
    }

    @Test
    void missingDetailInThisWorkoutReturnsEmptyWithoutSaving() {
        stubWorkout();
        when(detailRepository.findByDetailIdAndWorkout_workoutId(DETAIL_ID, WORKOUT_ID))
                .thenReturn(Optional.empty());

        assertTrue(approve(60).isEmpty());

        verify(detailRepository, never()).saveAndFlush(any());
    }

    @Test
    void completedDetailCannotBeChanged() {
        detail.start(START);
        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(30), Duration.ofSeconds(30));
        detail.recordHeartbeat(detail.getTimerToken(), START.plusSeconds(60), Duration.ofSeconds(30));
        assertTrue(detail.complete(detail.getTimerToken(), START.plusSeconds(60),
                Duration.ofSeconds(30), new BigDecimal("65.0"), null, null, null));
        OffsetDateTime completedAt = detail.getCompletedAt();
        BigDecimal calories = detail.getCaloriesBurned();
        stubDetail();

        assertThrows(IllegalStateException.class, () -> approve(60));

        verify(detailRepository, never()).saveAndFlush(any());
        assertNull(detail.getApprovedRestSecondsSnapshot());
        assertEquals(completedAt, detail.getCompletedAt());
        assertEquals(calories, detail.getCaloriesBurned());
    }

    @Test
    void updatingApprovalDuringRestKeepsExistingDeadlineTokenAndCounters() {
        detail.start(START);
        detail.approveRestDuration(60);
        detail.startRest(detail.getTimerToken(), START.plusSeconds(10), Duration.ofSeconds(30));
        UUID token = detail.getTimerToken();
        stubDetail();
        when(detailRepository.saveAndFlush(detail)).thenReturn(detail);

        WorkoutRestApprovalResponse response = approve(120).orElseThrow();

        assertEquals(Integer.valueOf(120), response.getApprovedRestSecondsSnapshot());
        assertEquals(START.plusSeconds(70), detail.getRestEndsAt());
        assertEquals(START.plusSeconds(10), detail.getLastRecordedAt());
        assertEquals(START.plusSeconds(10), detail.getLastHeartbeatAt());
        assertEquals(WorkoutDetailStatus.RESTING, detail.getStatus());
        assertEquals(token, detail.getTimerToken());
        assertEquals(10_000L, detail.getActiveDurationMillis());
        assertEquals(0L, detail.getRestDurationMillis());
    }

    private Optional<WorkoutRestApprovalResponse> approve(Integer seconds) {
        return service.approveWorkoutDetailRestDuration(USERNAME, WORKOUT_ID, DETAIL_ID, request(seconds));
    }

    private WorkoutRestApprovalRequest request(Integer seconds) {
        WorkoutRestApprovalRequest request = new WorkoutRestApprovalRequest();
        request.setRestSeconds(seconds);
        return request;
    }

    private void stubWorkout() {
        when(userRepository.findWorkoutAccessForUpdate(USERNAME)).thenReturn(Optional.of(workoutAccess()));
        when(workoutRepository.findByIdForUpdate(WORKOUT_ID)).thenReturn(Optional.of(workout));
    }

    private void stubDetail() {
        stubWorkout();
        when(detailRepository.findByDetailIdAndWorkout_workoutId(DETAIL_ID, WORKOUT_ID))
                .thenReturn(Optional.of(detail));
    }
}
