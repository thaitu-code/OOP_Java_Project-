package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.ExerciseRequest;
import vn.id.fitnessai.dto.ExerciseResponse;
import vn.id.fitnessai.service.ExerciseService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class ExerciseController {
    private final ExerciseService exerciseService;

    @PostMapping("/admin/exercises")
    public ResponseEntity<ExerciseResponse> createExercise(@Valid @RequestBody ExerciseRequest request){
        ExerciseResponse response = exerciseService.createExercise(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException exception){
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @GetMapping("/exercises")
    public ResponseEntity<List<ExerciseResponse>> getAllExercises(){
        List<ExerciseResponse> responses = exerciseService.getAllExercises();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/exercises/{exerciseId}")
    public ResponseEntity<ExerciseResponse> getExerciseById(@PathVariable("exerciseId") Integer exerciseId){
        Optional<ExerciseResponse> response = exerciseService.getExerciseById(exerciseId);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PutMapping("/admin/exercises/{exerciseId}")
    public ResponseEntity<ExerciseResponse> updateExercise(@PathVariable("exerciseId") Integer exerciseId,
                                                           @Valid @RequestBody ExerciseRequest request){
        //Không cần xử lý ID vì sẽ đẩy xuống service xử lý
        Optional<ExerciseResponse> response = exerciseService.updateExercise(exerciseId, request);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }


    @PatchMapping("/admin/exercises/{exerciseId}/deactivate")
    public ResponseEntity<ExerciseResponse> deactivateExercise(@PathVariable("exerciseId") Integer exerciseId){
        Optional<ExerciseResponse> response = exerciseService.updateExerciseStatus(exerciseId, false);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/exercises/{exerciseId}/activate")
    public ResponseEntity<ExerciseResponse> activateExercise(@PathVariable("exerciseId") Integer exerciseId){
        Optional<ExerciseResponse> response = exerciseService.updateExerciseStatus(exerciseId, true);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }
}
