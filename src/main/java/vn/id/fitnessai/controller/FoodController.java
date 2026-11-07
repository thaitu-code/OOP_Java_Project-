package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.dto.FoodRequest;
import vn.id.fitnessai.dto.FoodResponse;
import vn.id.fitnessai.service.FoodService;

import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FoodController {

    private final FoodService foodService;

    @PostMapping("/admin/foods")
    public ResponseEntity<FoodResponse> createFood(
            @Valid @RequestBody FoodRequest request
    ) {
        FoodResponse response = foodService.createFood(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : "Dữ liệu không hợp lệ";

        return ResponseEntity.badRequest()
                .body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        FieldError error =
                exception.getBindingResult().getFieldError();

        String message =
                error != null && error.getDefaultMessage() != null
                        ? error.getDefaultMessage()
                        : "Dữ liệu không hợp lệ";

        return ResponseEntity.badRequest()
                .body(Map.of("message", message));
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

        if ("uk_food_name_key".equals(constraintName)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "message",
                            "Tên thực phẩm đã tồn tại"
                    ));
        }

        log.error(
                "Food storage failure: constraint={}, causeType={}",
                constraintName,
                exception.getMostSpecificCause()
                        .getClass()
                        .getSimpleName()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message",
                        "Không thể lưu thực phẩm do lỗi hệ thống"
                ));
    }

    @GetMapping("/foods")
    public ResponseEntity<PagedModel<FoodResponse>> getActiveFoods(
            @RequestParam(name = "keyword", defaultValue = "")
            String keyword,

            @RequestParam(name = "categoryId", required = false)
            Integer categoryId,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        Page<FoodResponse> foods = foodService.getActiveFoods(
                keyword,
                categoryId,
                page,
                size
        );

        return ResponseEntity.ok(new PagedModel<>(foods));
    }

    @GetMapping("/foods/{foodId}")
    public ResponseEntity<FoodResponse> getActiveFoodById(
            @PathVariable("foodId") Integer foodId
    ) {
        Optional<FoodResponse> response = foodService.getActiveFoodById(foodId);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @GetMapping("/admin/foods")
    public ResponseEntity<PagedModel<FoodResponse>> getAllFoods(
            @RequestParam(name = "keyword", defaultValue = "")
            String keyword,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        Page<FoodResponse> foods = foodService.getAllFoods(
                keyword,
                page,
                size
        );

        return ResponseEntity.ok(new PagedModel<>(foods));
    }

    @PutMapping("/admin/foods/{foodId}")
    public ResponseEntity<FoodResponse> updateFood(
            @PathVariable("foodId") Integer foodId,
            @Valid @RequestBody FoodRequest request
    ) {
        Optional<FoodResponse> response =
                foodService.updateFood(foodId, request);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/foods/{foodId}/deactivate")
    public ResponseEntity<FoodResponse> deactivateFood(
            @PathVariable("foodId") Integer foodId
    ) {
        Optional<FoodResponse> response =
                foodService.updateFoodStatus(foodId, false);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }

    @PatchMapping("/admin/foods/{foodId}/activate")
    public ResponseEntity<FoodResponse> activateFood(
            @PathVariable("foodId") Integer foodId
    ) {
        Optional<FoodResponse> response =
                foodService.updateFoodStatus(foodId, true);

        if (response.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response.get());
    }
}