package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.service.WorkoutSelectionService;
import java.security.Principal;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class WorkoutSelectionController {
    private final WorkoutSelectionService service;
    @PostMapping("/workouts/from-selection")
    public ResponseEntity<WorkoutResponse> create(Principal principal, @Valid @RequestBody WorkoutSelectionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.create(principal.getName(), request));
    }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, SecurityException.class})
    ResponseEntity<Map<String,String>> error(RuntimeException e) {
        return ResponseEntity.status(e instanceof SecurityException ? 403 : e instanceof IllegalStateException ? 409 : 400).body(Map.of("message", e.getMessage()));
    }
}
