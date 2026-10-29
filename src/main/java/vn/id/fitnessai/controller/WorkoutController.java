package vn.id.fitnessai.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.WorkoutDetailRequest;
import vn.id.fitnessai.dto.WorkoutDetailResponse;
import vn.id.fitnessai.dto.WorkoutResponse;
import vn.id.fitnessai.dto.WorkoutTimerResponse;
import vn.id.fitnessai.service.WorkoutService;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class WorkoutController {
    private final WorkoutService workoutService;

    @PostMapping("/workouts")
    public ResponseEntity<WorkoutResponse> createWorkout(Authentication authentication){

        WorkoutResponse response = workoutService.createWorkout(authentication.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

}
