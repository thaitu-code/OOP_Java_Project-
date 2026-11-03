package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.service.ExerciseLevelService;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/admin/exercises/{exerciseId}/levels")
public class ExerciseLevelController {
    private final ExerciseLevelService service;
    @PostMapping
    public ResponseEntity<ExerciseLevelResponse> create(@PathVariable Integer exerciseId, @Valid @RequestBody ExerciseLevelRequest request) {
        return ResponseEntity.status(201).body(service.save(exerciseId, null, request));
    }
    @PutMapping("/{levelId}")
    public ExerciseLevelResponse update(@PathVariable Integer exerciseId, @PathVariable Integer levelId,
                                        @Valid @RequestBody ExerciseLevelRequest request) {
        return service.save(exerciseId, levelId, request);
    }
    @PatchMapping("/{levelId}/activate")
    public ExerciseLevelResponse activate(@PathVariable Integer exerciseId, @PathVariable Integer levelId) {
        return service.status(exerciseId, levelId, true);
    }
    @PatchMapping("/{levelId}/deactivate")
    public ExerciseLevelResponse deactivate(@PathVariable Integer exerciseId, @PathVariable Integer levelId) {
        return service.status(exerciseId, levelId, false);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }
}
