package vn.id.fitnessai.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.id.fitnessai.dto.WorkoutResponse;
import vn.id.fitnessai.service.WorkoutService;

import java.util.Map;

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
         return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(AccessDeniedException exception){
         return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
