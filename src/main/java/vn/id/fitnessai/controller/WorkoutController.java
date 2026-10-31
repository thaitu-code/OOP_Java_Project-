package vn.id.fitnessai.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.service.WorkoutService;
import vn.id.fitnessai.service.WorkoutTrackingService;
import vn.id.fitnessai.enums.WorkoutStatus;

import java.time.Duration;
import java.sql.SQLException;
import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WorkoutController {
    private final WorkoutService workoutService;
    private final WorkoutTrackingService workoutTrackingService;

    @Value("${fitness.workout.heartbeat-timeout-seconds}")
    private long heartbeatTimeoutSeconds;

    @PostMapping("/workouts")
    public ResponseEntity<WorkoutResponse> createWorkout(Authentication authentication){

        WorkoutResponse response = workoutService.createWorkout(authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/workouts")
    public WorkoutPageResponse getWorkouts(Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) WorkoutStatus status) {
        return workoutService.getWorkouts(authentication.getName(), page, size, status);
    }

    @GetMapping("/workouts/current")
    public ResponseEntity<WorkoutResponse> getCurrentWorkout(Authentication authentication) {
        return workoutService.getCurrentWorkout(authentication.getName())
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/workouts/current/state")
    public ResponseEntity<WorkoutSessionStateResponse> getCurrentWorkoutState(Authentication authentication) {
        return workoutService.getCurrentWorkoutState(authentication.getName())
                .map(response -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/workouts/{workoutId}")
    public ResponseEntity<WorkoutResponse> getWorkout(Authentication authentication,
            @PathVariable Integer workoutId) {
        return workoutService.getWorkout(authentication.getName(), workoutId)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/workouts/{workoutId}/complete")
    public ResponseEntity<WorkoutResponse> completeWorkout(Authentication authentication,
            @PathVariable Integer workoutId) {
        return workoutTrackingService.completeWorkout(authentication.getName(), workoutId)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/workouts/{workoutId}/cancel")
    public ResponseEntity<WorkoutResponse> cancelWorkout(Authentication authentication,
            @PathVariable Integer workoutId) {
        return workoutTrackingService.cancelWorkout(authentication.getName(), workoutId)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/workouts/{workoutId}/details/remaining")
    public ResponseEntity<List<WorkoutDetailResponse>> getRemainingWorkoutDetails(Authentication authentication,
            @PathVariable Integer workoutId) {
        return workoutService.getRemainingWorkoutDetails(authentication.getName(), workoutId)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/workouts/{workoutId}/details/{detailId}/timer")
    public ResponseEntity<WorkoutTimerResponse> getWorkoutDetailTimer(Authentication authentication,
            @PathVariable Integer workoutId, @PathVariable Integer detailId) {
        return workoutTrackingService.getWorkoutDetailTimer(authentication.getName(), workoutId, detailId)
                .map(response -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/rest")
    public ResponseEntity<WorkoutTimerResponse> startWorkoutDetailRest(Authentication authentication,
            @PathVariable Integer workoutId, @PathVariable Integer detailId,
            @Valid @RequestBody WorkoutHeartbeatRequest request) {
        return workoutTrackingService.startWorkoutDetailRest(authentication.getName(), workoutId,
                        detailId, request, Duration.ofSeconds(heartbeatTimeoutSeconds))
                .map(response -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/pause")
    public ResponseEntity<WorkoutTimerResponse> pauseWorkoutDetail(Authentication authentication,
            @PathVariable Integer workoutId, @PathVariable Integer detailId,
            @Valid @RequestBody WorkoutHeartbeatRequest request) {
        return workoutTrackingService.pauseWorkoutDetail(authentication.getName(), workoutId,
                        detailId, request, Duration.ofSeconds(heartbeatTimeoutSeconds))
                .map(response -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/resume")
    public ResponseEntity<WorkoutTimerResponse> resumeWorkoutDetail(Authentication authentication,
            @PathVariable Integer workoutId, @PathVariable Integer detailId,
            @Valid @RequestBody WorkoutHeartbeatRequest request) {
        return workoutTrackingService.resumeWorkoutDetail(authentication.getName(), workoutId,
                        detailId, request, Duration.ofSeconds(heartbeatTimeoutSeconds))
                .map(response -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception){
         return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalStateException exception){
         return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(AccessDeniedException exception){
         return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataConflict(DataIntegrityViolationException exception) {
        String constraint = "unknown";
        String sqlState = "unknown";
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 16; depth++) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                constraint = violation.getConstraintName() == null ? "unknown" : violation.getConstraintName();
            }
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                sqlState = sql.getSQLState();
            }
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        // Chi log metadata loi; khong in SQL, gia tri dong du lieu hay thong tin dang nhap.
        log.warn("Workout database conflict: constraint={}, SQLState={}, causeType={}",
                constraint, sqlState, exception.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                "Dữ liệu tập luyện xung đột với ràng buộc database; hãy tải lại trạng thái buổi tập"));
    }

    @PostMapping("/workouts/{workoutId}/details")
    public ResponseEntity<WorkoutDetailResponse> addExerciseToWorkout(Authentication authentication,
                                                                      @PathVariable("workoutId") Integer workoutId,
                                                                      @Valid @RequestBody WorkoutDetailRequest request) {
        Optional<WorkoutDetailResponse> response = workoutService.addExerciseToWorkout(authentication.getName(), workoutId, request);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(response.get());
    }

    @GetMapping("/workouts/{workoutId}/details")
    public ResponseEntity<List<WorkoutDetailResponse>> getWorkoutDetails(Authentication authentication,
                                                                         @PathVariable("workoutId") Integer workoutId){
        Optional<List<WorkoutDetailResponse>> responses = workoutService.getWorkoutDetails(authentication.getName(), workoutId);

        if (responses.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(responses.get());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/start")
    public ResponseEntity<WorkoutTimerResponse> startWorkoutDetail(
            Authentication authentication,
            @PathVariable("workoutId") Integer workoutId,
            @PathVariable("detailId") Integer detailId
    ){
        Optional<WorkoutTimerResponse> response = workoutService.startWorkoutDetail(authentication.getName(), workoutId, detailId);
        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/heartbeat")
    public ResponseEntity<WorkoutTimerResponse> recordWorkoutDetailHeartbeat(
            Authentication authentication,
            @PathVariable("workoutId") Integer workoutId,
            @PathVariable("detailId") Integer detailId,
            @Valid @RequestBody WorkoutHeartbeatRequest request
            )
    {
        Optional<WorkoutTimerResponse> response = workoutTrackingService.recordWorkoutDetailHeartbeat(
                authentication.getName(),
                workoutId,
                detailId,
                request,
                Duration.ofSeconds(heartbeatTimeoutSeconds)
        );

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/complete")
    public ResponseEntity<WorkoutDetailResponse> completeWorkoutDetail(
            Authentication authentication,
            @PathVariable("workoutId") Integer workoutId,
            @PathVariable("detailId") Integer detailId,
            @Valid @RequestBody WorkoutDetailCompleteRequest request
    ){
        Optional<WorkoutDetailResponse> response = workoutTrackingService.completeWorkoutDetail(
                authentication.getName(),
                workoutId,
                detailId,
                request,
                Duration.ofSeconds(heartbeatTimeoutSeconds)
        );

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/rest-duration")
    public ResponseEntity<WorkoutRestApprovalResponse> approveWorkoutDetailRestDuration(
            Authentication authentication,
            @PathVariable("workoutId") Integer workoutId,
            @PathVariable("detailId") Integer detailId,
            @Valid @RequestBody WorkoutRestApprovalRequest request
    ) {
        Optional<WorkoutRestApprovalResponse> response =
                workoutTrackingService.approveWorkoutDetailRestDuration(
                        authentication.getName(), workoutId, detailId, request
                );

        return response.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/workouts/{workoutId}/details/{detailId}/recover")
    public ResponseEntity<WorkoutTimerResponse> recoverWorkoutDetailTimer(
            Authentication authentication,
            @PathVariable("workoutId") Integer workoutId,
            @PathVariable("detailId") Integer detailId
    ) {
        Optional<WorkoutTimerResponse> response =
                workoutTrackingService.recoverWorkoutDetailTimer(
                        authentication.getName(),
                        workoutId,
                        detailId
                );

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }
}
