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
@RequiredArgsConstructor
public class ExerciseCategoryController {
    private final ExerciseCategoryService exerciseCategoryService;

    @PostMapping("/admin/exercise-categories")  //Chỉ role admin mới được tạo nhóm bài tập mới
    public ResponseEntity<ExerciseCategoryResponse> createCategory(@Valid @RequestBody ExerciseCategoryRequest request){
        ExerciseCategoryResponse response = exerciseCategoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,String>> handleIllegalArgument(IllegalArgumentException exception){
        return ResponseEntity.badRequest().body(Map.of("message",exception.getMessage()));
    }

    @GetMapping("/exercise-categories")  //Cả User và Admin đều xem được danh sách bài tập
    public ResponseEntity<List<ExerciseCategoryResponse>> getAllCategories(){
        List<ExerciseCategoryResponse> responses = exerciseCategoryService.getAllCategories();

        return ResponseEntity.ok(responses);
    }
}
