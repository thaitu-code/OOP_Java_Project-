package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.ExerciseCategoryRequest;
import vn.id.fitnessai.dto.ExerciseCategoryResponse;
import vn.id.fitnessai.service.ExerciseCategoryService;

import java.util.*;

@RestController
@RequestMapping("/admin/exercise-categories")
@RequiredArgsConstructor
public class ExerciseCategoryController {
    private final ExerciseCategoryService exerciseCategoryService;

    @PostMapping
    public ResponseEntity<ExerciseCategoryResponse> createCategory(@Valid @RequestBody ExerciseCategoryRequest request){
        ExerciseCategoryResponse response = exerciseCategoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> handleIllegalArgument(IllegalArgumentException exception){
        return ResponseEntity.badRequest().body(Map.of("message",exception.getMessage()));
    }
}
