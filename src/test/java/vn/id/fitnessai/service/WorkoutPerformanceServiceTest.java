package vn.id.fitnessai.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.id.fitnessai.dto.WorkoutHeartbeatRequest;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.enums.WorkoutDetailStatus;
import vn.id.fitnessai.repository.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkoutPerformanceServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WorkoutRepository workouts = mock(WorkoutRepository.class);
    private final WorkoutDetailRepository details = mock(WorkoutDetailRepository.class);
    private final ExerciseRepository exercises = mock(ExerciseRepository.class);

    private UserRepository.WorkoutAccess access(long id, boolean enabled, boolean locked) {
        return new UserRepository.WorkoutAccess() {
            public Long getId() { return id; }
            public boolean getEnabled() { return enabled; }
            public boolean getLocked() { return locked; }
        };
    }

    @Test
    void createUsesLockedAccessAndUserReferenceWithoutHydratingProfileOrRoles() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutService(workouts, users, exercises, details, factory.getValidator());
            User reference = new User(); reference.setId(7L);
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(users.getReferenceById(7L)).thenReturn(reference);
            when(workouts.save(any(Workout.class))).thenAnswer(call -> {
                Workout saved = call.getArgument(0); ReflectionTestUtils.setField(saved, "workoutId", 20); return saved;
            });
            var response = service.createWorkout("tester");
            assertEquals(WorkoutStatus.IN_PROGRESS, response.getStatus());
            assertEquals(7L, response.getUserId());
            verify(users).findWorkoutAccessForUpdate("tester"); verify(users).getReferenceById(7L);
            verifyNoMoreInteractions(users); verifyNoInteractions(details, exercises);
        }
    }

    @Test
    void addKeepsAccountWorkoutExerciseLockOrderAndSelectionSnapshot() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutService(workouts, users, exercises, details, factory.getValidator());
            User owner = new User(); owner.setId(7L);
            Workout workout = new Workout(owner, OffsetDateTime.now()); ReflectionTestUtils.setField(workout, "workoutId", 20);
            Exercise exercise = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Group"));
            ReflectionTestUtils.setField(exercise, "exerciseId", 5);
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByIdForUpdate(20)).thenReturn(Optional.of(workout));
            when(exercises.findByIdForUpdate(5)).thenReturn(Optional.of(exercise));
            when(details.save(any(WorkoutDetail.class))).thenAnswer(call -> call.getArgument(0));
            WorkoutDetailRequest request = new WorkoutDetailRequest(); request.setExerciseId(5);
            var response = service.addExerciseToWorkout("tester", 20, request).orElseThrow();
            assertEquals("Test", response.getExerciseNameSnapshot());
            assertNull(response.getStartedAt());
            var order = inOrder(users, workouts, exercises);
            order.verify(users).findWorkoutAccessForUpdate("tester");
            order.verify(workouts).findByIdForUpdate(20); order.verify(exercises).findByIdForUpdate(5);
        }
    }

    @Test
    void snapshotUsesOneAccountReadAndOneDetailListWithoutIssuingAToken() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutService(workouts, users, exercises, details, factory.getValidator());
            User user = new User(); user.setId(7L);
            Workout workout = new Workout(user, OffsetDateTime.now());
            ReflectionTestUtils.setField(workout, "workoutId", 20);
            WorkoutDetail detail = new WorkoutDetail(workout, new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Test")));
            detail.start(OffsetDateTime.now());
            when(users.findWorkoutAccessByUsername("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByUser_IdAndStatus(7L, WorkoutStatus.IN_PROGRESS)).thenReturn(Optional.of(workout));
            when(details.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(20)).thenReturn(List.of(detail));
            var result = service.getCurrentWorkoutState("tester").orElseThrow();
            assertEquals(1, result.details().size());
            assertNotNull(result.timer()); assertNull(result.timer().getTimerToken());
            assertNotNull(detail.getTimerToken(), "Read does not rotate or remove the actual lease");
            verify(users).findWorkoutAccessByUsername("tester");
            verify(details).findAllByWorkout_WorkoutIdOrderByDetailIdAsc(20);
            verifyNoMoreInteractions(users, details);
        }
    }

    @Test
    void unusableAccountIsRejectedBeforeLoadingWorkout() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutService(workouts, users, exercises, details, factory.getValidator());
            when(users.findWorkoutAccessByUsername("tester")).thenReturn(Optional.of(access(7, true, true)));
            assertThrows(AccessDeniedException.class, () -> service.getCurrentWorkoutState("tester"));
            verifyNoInteractions(workouts, details);
        }
    }

    @Test
    void noCurrentWorkoutDoesNotLoadDetails() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutService(workouts, users, exercises, details, factory.getValidator());
            when(users.findWorkoutAccessByUsername("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByUser_IdAndStatus(7L, WorkoutStatus.IN_PROGRESS)).thenReturn(Optional.empty());
            assertTrue(service.getCurrentWorkoutState("tester").isEmpty());
            verifyNoInteractions(details);
        }
    }

    @Test
    void heartbeatKeepsUserThenWorkoutLockAndRejectsOtherOwnerBeforeDetail() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutTrackingService(workouts, details, users,
                    mock(CheckInStreakService.class), mock(BadgeService.class), factory.getValidator());
            User owner = new User(); owner.setId(9L);
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByIdForUpdate(20)).thenReturn(Optional.of(new Workout(owner, OffsetDateTime.now())));
            WorkoutHeartbeatRequest request = new WorkoutHeartbeatRequest();
            request.setTimerToken(java.util.UUID.randomUUID());
            assertThrows(AccessDeniedException.class, () -> service.recordWorkoutDetailHeartbeat("tester", 20, 30, request, Duration.ofSeconds(30)));
            var order = inOrder(users, workouts);
            order.verify(users).findWorkoutAccessForUpdate("tester"); order.verify(workouts).findByIdForUpdate(20);
            verifyNoInteractions(details); verify(users, never()).findByUsernameForUpdate(any());
        }
    }

    @Test
    void cancelUsesAccessProjectionAndPreservesCompletedResultsWhileCancellingActiveDetail() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var checkIns = mock(CheckInStreakService.class);
            var badges = mock(BadgeService.class);
            var service = new WorkoutTrackingService(workouts, details, users, checkIns, badges, factory.getValidator());
            User owner = new User(); owner.setId(7L);
            var now = OffsetDateTime.now();
            Workout workout = new Workout(owner, now.minusMinutes(1));
            ReflectionTestUtils.setField(workout, "workoutId", 20);
            Exercise exercise = new Exercise("Test", BigDecimal.ONE, new BigDecimal("0.1"), new ExerciseCategory("Group"));
            WorkoutDetail completed = new WorkoutDetail(workout, exercise);
            completed.start(now.minusSeconds(20));
            completed.recordHeartbeat(completed.getTimerToken(), now.minusSeconds(10), Duration.ofSeconds(30));
            assertTrue(completed.complete(completed.getTimerToken(), now, Duration.ofSeconds(30),
                    new BigDecimal("60"), null, null, null));
            var completedAt = completed.getCompletedAt();
            var calories = completed.getCaloriesBurned();
            WorkoutDetail active = new WorkoutDetail(workout, exercise); active.start(now);
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByIdForUpdate(20)).thenReturn(Optional.of(workout));
            when(details.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(20)).thenReturn(List.of(completed, active));
            when(workouts.saveAndFlush(workout)).thenReturn(workout);

            var result = service.cancelWorkout("tester", 20).orElseThrow();

            assertEquals(WorkoutStatus.CANCELLED, result.getStatus());
            assertEquals(calories, result.getTotalCaloriesBurned());
            assertEquals(WorkoutDetailStatus.COMPLETED, completed.getStatus());
            assertEquals(completedAt, completed.getCompletedAt());
            assertEquals(WorkoutDetailStatus.CANCELLED, active.getStatus()); assertNull(active.getTimerToken());
            var order = inOrder(users, workouts);
            order.verify(users).findWorkoutAccessForUpdate("tester"); order.verify(workouts).findByIdForUpdate(20);
            verifyNoMoreInteractions(users); verifyNoInteractions(checkIns, badges);
        }
    }

    @Test
    void cancelRejectsOtherOwnerBeforeReadingOrChangingDetails() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutTrackingService(workouts, details, users,
                    mock(CheckInStreakService.class), mock(BadgeService.class), factory.getValidator());
            User owner = new User(); owner.setId(9L);
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByIdForUpdate(20)).thenReturn(Optional.of(new Workout(owner, OffsetDateTime.now())));
            assertThrows(AccessDeniedException.class, () -> service.cancelWorkout("tester", 20));
            verifyNoInteractions(details); verify(workouts, never()).saveAndFlush(any());
        }
    }

    @Test
    void cancelRejectsLockedAndUnverifiedAccountsBeforeLockingWorkout() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutTrackingService(workouts, details, users,
                    mock(CheckInStreakService.class), mock(BadgeService.class), factory.getValidator());
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, true)));
            assertThrows(AccessDeniedException.class, () -> service.cancelWorkout("tester", 20));
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, false, false)));
            assertThrows(AccessDeniedException.class, () -> service.cancelWorkout("tester", 20));
            verifyNoInteractions(workouts, details);
        }
    }

    @Test
    void cancellingAlreadyCancelledWorkoutDoesNotSaveOrAlterItsEndTime() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new WorkoutTrackingService(workouts, details, users,
                    mock(CheckInStreakService.class), mock(BadgeService.class), factory.getValidator());
            User owner = new User(); owner.setId(7L);
            var end = OffsetDateTime.now().minusDays(1);
            Workout workout = new Workout(owner, end.minusMinutes(10));
            ReflectionTestUtils.setField(workout, "workoutId", 20);
            workout.setStatus(WorkoutStatus.CANCELLED); workout.setEndTime(end);
            when(users.findWorkoutAccessForUpdate("tester")).thenReturn(Optional.of(access(7, true, false)));
            when(workouts.findByIdForUpdate(20)).thenReturn(Optional.of(workout));
            when(details.findAllByWorkout_WorkoutIdOrderByDetailIdAsc(20)).thenReturn(List.of());
            assertEquals(WorkoutStatus.CANCELLED, service.cancelWorkout("tester", 20).orElseThrow().getStatus());
            assertEquals(end, workout.getEndTime());
            verify(workouts, never()).saveAndFlush(any()); verify(details, never()).saveAllAndFlush(any());
        }
    }
}
