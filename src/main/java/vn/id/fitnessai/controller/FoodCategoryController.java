package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.parameters.P;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.FoodCategoryRequest;
import vn.id.fitnessai.dto.FoodCategoryResponse;
import vn.id.fitnessai.entity.FoodCategory;
import vn.id.fitnessai.service.FoodCategoryService;

import java.util.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FoodCategoryController {
    private final FoodCategoryService foodCategoryService;

    @PostMapping("/admin/food-categories")
    public ResponseEntity<FoodCategoryResponse> createCategory(@Valid @RequestBody FoodCategoryRequest request){
        FoodCategoryResponse response = foodCategoryService.createCategory(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception){
        String message = exception.getMessage() != null ? exception.getMessage() : "Dữ liệu không hợp lệ";

        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,String>> handleValidation(MethodArgumentNotValidException exception) {
        FieldError error = exception.getBindingResult().getFieldError();

        String message = error != null && error.getDefaultMessage() != null
                ? error.getDefaultMessage() : "Dữ liệu không hợp lệ";

        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(
            DataIntegrityViolationException exception
    ) {
        String constraintName = null;
        Throwable cause = exception;

        for (int depth = 0; cause != null && depth < 16; depth++) {
            if (cause instanceof ConstraintViolationException violation) {
                constraintName = violation.getConstraintName();
                break;
            }

            cause = cause.getCause();
        }

        if ("uk_food_category_name_key".equals(constraintName)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Tên nhóm thực phẩm đã tồn tại"
                    ));
        }

        log.error(
                "FoodCategory storage failure: constraint={}, causeType={}",
                constraintName,
                exception.getMostSpecificCause().getClass().getSimpleName()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message",
                        "Không thể lưu nhóm thực phẩm do lỗi hệ thống"
                ));
    }

    @GetMapping("/food-categories")
    public ResponseEntity<List<FoodCategoryResponse>> getActiveCategories(){
        List<FoodCategoryResponse> responses = foodCategoryService.getActiveCategories();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/admin/food-categories")
    public ResponseEntity<List<FoodCategoryResponse>> getAllCategories() {
        List<FoodCategoryResponse> responses =
                foodCategoryService.getAllCategories();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/food-categories/{categoryId}")
    public ResponseEntity<FoodCategoryResponse> getActiveCategoryById(
            @PathVariable("categoryId") Integer categoryId
    ){
        Optional<FoodCategoryResponse> response = foodCategoryService.getActiveCategoryById(categoryId);

        if (response.isEmpty()){
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PutMapping("/admin/food-categories/{categoryId}")
    public ResponseEntity<FoodCategoryResponse> updateCategory(
            @PathVariable("categoryId") Integer categoryId,
            @Valid @RequestBody FoodCategoryRequest request
    ) {
        Optional<FoodCategoryResponse> response =
                foodCategoryService.updateCategory(categoryId, request);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/food-categories/{categoryId}/deactivate")
    public ResponseEntity<FoodCategoryResponse> deactivateCategory(
            @PathVariable("categoryId") Integer categoryId
    ) {
        Optional<FoodCategoryResponse> response =
                foodCategoryService.updateCategoryStatus(categoryId, false);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/food-categories/{categoryId}/activate")
    public ResponseEntity<FoodCategoryResponse> activateCategory(
            @PathVariable("categoryId") Integer categoryId
    ) {
        Optional<FoodCategoryResponse> response =
                foodCategoryService.updateCategoryStatus(categoryId, true);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }
}
