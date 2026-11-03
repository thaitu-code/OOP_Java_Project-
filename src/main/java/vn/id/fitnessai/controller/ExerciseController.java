package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.ExerciseRequest;
import vn.id.fitnessai.dto.ExerciseResponse;
import vn.id.fitnessai.service.ExerciseService;

import java.util.*;
import java.sql.SQLException;

@RestController
@RequiredArgsConstructor
@Slf4j
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

    @DeleteMapping("/admin/exercises/{exerciseId}")
    public ResponseEntity<ExerciseResponse> deleteExercise(@PathVariable("exerciseId") Integer exerciseId){
        boolean deleted = exerciseService.deleteExercise(exerciseId);

        if (!deleted){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(
            IllegalStateException exception) {

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
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
            if (cause instanceof SQLException sql && sql.getSQLState() != null) sqlState = sql.getSQLState();
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        log.warn("Exercise database conflict: constraint={}, SQLState={}, causeType={}",
                constraint, sqlState, exception.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                "Không thể lưu hoặc xóa bài tập do ràng buộc dữ liệu. Hãy tải lại danh sách; nếu vẫn lỗi, kiểm tra log máy chủ."));
    }
}
