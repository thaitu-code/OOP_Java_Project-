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

    @GetMapping("/exercise-categories/{categoryId}")
    public ResponseEntity<ExerciseCategoryResponse> getCategoryById(@PathVariable("categoryId") Integer categoryId){
        Optional<ExerciseCategoryResponse> response = exerciseCategoryService.getCategoryById(categoryId);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/exercise-categories/{categoryId}")
    public ResponseEntity<ExerciseCategoryResponse> updateCategory(
            @PathVariable("categoryId") Integer categoryId,
            @Valid @RequestBody ExerciseCategoryRequest request){

        Optional<ExerciseCategoryResponse> response = exerciseCategoryService.updateCategory(categoryId, request);
        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @DeleteMapping("/admin/exercise-categories/{categoryId}")
    public ResponseEntity<ExerciseCategoryResponse> deactivateCategory(@PathVariable("categoryId") Integer categoryId){
        Optional<ExerciseCategoryResponse> response = exerciseCategoryService.updateCategoryStatus(categoryId,false);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/exercise-categories/{categoryId}/activate")
    public ResponseEntity<ExerciseCategoryResponse> activateCategory(@PathVariable("categoryId") Integer categoryId){
        Optional<ExerciseCategoryResponse> response = exerciseCategoryService.updateCategoryStatus(categoryId, true);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }
}
