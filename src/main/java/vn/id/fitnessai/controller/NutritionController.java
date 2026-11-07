package vn.id.fitnessai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.id.fitnessai.dto.LogDetailRequest;
import vn.id.fitnessai.dto.LogDetailUpdateRequest;
import vn.id.fitnessai.dto.NutritionLogHistoryResponse;
import vn.id.fitnessai.dto.NutritionLogResponse;
import vn.id.fitnessai.service.NutritionService;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/nutrition-logs")
    public ResponseEntity<NutritionLogResponse> getDailyLog(
            Authentication authentication,

            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        NutritionLogResponse response = nutritionService.getDailyLog(
                authentication.getName(),
                date
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/nutrition-logs/today/details")
    public ResponseEntity<NutritionLogResponse> addFood(
            Authentication authentication,
            @Valid @RequestBody LogDetailRequest request
    ) {
        NutritionLogResponse response = nutritionService.addFood(
                authentication.getName(),
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                messageOf(exception, "Dữ liệu không hợp lệ")
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        FieldError fieldError =
                exception.getBindingResult().getFieldError();

        String message =
                fieldError != null && fieldError.getDefaultMessage() != null
                        ? fieldError.getDefaultMessage()
                        : "Dữ liệu không hợp lệ";

        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody(
            HttpMessageNotReadableException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "JSON không hợp lệ. Kiểm tra kiểu dữ liệu và giá trị bữa ăn"
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return error(
                HttpStatus.BAD_REQUEST,
                "Tham số '" + exception.getName() + "' không hợp lệ"
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(
            AccessDeniedException exception
    ) {
        return error(
                HttpStatus.FORBIDDEN,
                messageOf(exception, "Bạn không có quyền thao tác")
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(
            IllegalStateException exception
    ) {
        return error(
                HttpStatus.CONFLICT,
                messageOf(
                        exception,
                        "Trạng thái dữ liệu đã thay đổi. Vui lòng tải lại nhật ký"
                )
        );
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<Map<String, String>> handleConcurrency(
            ConcurrencyFailureException exception
    ) {
        return error(
                HttpStatus.CONFLICT,
                "Dữ liệu đang được thay đổi. Vui lòng tải lại nhật ký trước khi thao tác tiếp"
        );
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleDataAccess(
            DataAccessException exception
    ) {
        log.error(
                "Nutrition storage failure: causeType={}",
                exception.getMostSpecificCause()
                        .getClass()
                        .getSimpleName()
        );

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Không thể xử lý nhật ký ăn uống do lỗi hệ thống"
        );
    }

    @PutMapping("/nutrition-logs/today/details/{detailId}")
    public ResponseEntity<NutritionLogResponse> updateDetail(
            Authentication authentication,
            @PathVariable("detailId") Integer detailId,
            @Valid @RequestBody LogDetailUpdateRequest request
    ) {
        Optional<NutritionLogResponse> response =
                nutritionService.updateDetail(
                        authentication.getName(),
                        detailId,
                        request
                );

        if (response.isEmpty()) {
            return ResponseEntity.notFound()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response.get());
    }

    @DeleteMapping("/nutrition-logs/today/details/{detailId}")
    public ResponseEntity<NutritionLogResponse> deleteDetail(
            Authentication authentication,
            @PathVariable("detailId") Integer detailId,
            @RequestParam(name = "expectedVersion") Long expectedVersion
    ) {
        Optional<NutritionLogResponse> response =
                nutritionService.deleteDetail(
                        authentication.getName(),
                        detailId,
                        expectedVersion
                );

        if (response.isEmpty()) {
            return ResponseEntity.notFound()
                    .cacheControl(CacheControl.noStore())
                    .build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response.get());
    }

    @GetMapping("/nutrition-logs/history")
    public ResponseEntity<PagedModel<NutritionLogHistoryResponse>> getLogHistory(
            Authentication authentication,

            @RequestParam(name = "page", defaultValue = "0")
            int page,

            @RequestParam(name = "size", defaultValue = "20")
            int size
    ) {
        Page<NutritionLogHistoryResponse> response =
                nutritionService.getLogHistory(
                        authentication.getName(),
                        page,
                        size
                );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PagedModel<>(response));
    }

    private ResponseEntity<Map<String, String>> error(
            HttpStatus status,
            String message
    ) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", message));
    }

    private String messageOf(
            RuntimeException exception,
            String fallback
    ) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? fallback
                : message;
    }
}