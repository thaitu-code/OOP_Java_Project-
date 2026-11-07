package vn.id.fitnessai.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.Food;
import vn.id.fitnessai.entity.FoodCategory;
import vn.id.fitnessai.entity.LogDetail;
import vn.id.fitnessai.entity.NutritionLog;
import vn.id.fitnessai.enums.MealType;
import vn.id.fitnessai.enums.NutritionEntrySource;
import vn.id.fitnessai.repository.FoodCategoryRepository;
import vn.id.fitnessai.repository.FoodRepository;
import vn.id.fitnessai.repository.LogDetailRepository;
import vn.id.fitnessai.repository.NutritionLogRepository;
import vn.id.fitnessai.repository.UserRepository;

import java.time.temporal.ChronoUnit;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NutritionService {

    private final NutritionLogRepository nutritionLogRepository;
    private final LogDetailRepository logDetailRepository;
    private final FoodRepository foodRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final UserRepository userRepository;
    private final Validator validator;
    private final Clock nutritionClock;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public NutritionLogResponse getDailyLog(
            String username,
            LocalDate date
    ) {
        Long userId = requireUserId(username);

        LocalDate requestedDate = date == null
                ? now().toLocalDate()
                : date;

        NutritionLog log = nutritionLogRepository
                .findByUser_IdAndLogDate(userId, requestedDate)
                .orElse(null);

        return buildResponse(log, userId, requestedDate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NutritionLogResponse addFood(
            String username,
            LogDetailRequest request
    ) {
        Long userId = requireUserId(username);
        validateRequest(request);

        OffsetDateTime startedAt = now();
        LocalDate logDate = startedAt.toLocalDate();

        nutritionLogRepository.createIfAbsent(
                userId,
                logDate,
                startedAt
        );

        NutritionLog log = nutritionLogRepository
                .findByUserIdAndLogDateForUpdate(userId, logDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Không thể lấy nhật ký ăn uống"
                ));

        Integer categoryId = foodRepository
                .findCategoryIdByFoodId(request.getFoodId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Thực phẩm không tồn tại"
                ));

        FoodCategory category = foodCategoryRepository
                .findByIdForLogging(categoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nhóm thực phẩm không tồn tại"
                ));

        Food food = foodRepository
                .findByIdForLogging(request.getFoodId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Thực phẩm không tồn tại"
                ));

        if (!Objects.equals(
                food.getCategory().getCategoryId(),
                category.getCategoryId()
        )) {
            throw new IllegalStateException(
                    "Thực phẩm vừa đổi nhóm. Vui lòng tải lại và thử lại"
            );
        }

        if (!food.isAvailableForLogging()) {
            throw new IllegalArgumentException(
                    "Thực phẩm hoặc nhóm đã ngừng hoạt động"
            );
        }

        if (!Objects.equals(userId, requireUserId(username))) {
            throw new AccessDeniedException(
                    "Thông tin tài khoản đã thay đổi"
            );
        }

        OffsetDateTime acceptedAt = now();
        log.requireEditableAt(acceptedAt);

        LogDetail detail = new LogDetail(
                log,
                food,
                request.getMealType(),
                request.getWeightGrams(),
                request.getNote(),
                NutritionEntrySource.USER,
                acceptedAt
        );

        logDetailRepository.saveAndFlush(detail);

        return buildResponse(log, userId, logDate);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<NutritionLogResponse> updateDetail(
            String username,
            Integer detailId,
            LogDetailUpdateRequest request
    ) {
        Long userId = requireUserId(username);
        validateDetailId(detailId);
        validateRequest(request);

        NutritionLog log = nutritionLogRepository
                .findByUserIdAndLogDateForUpdate(
                        userId,
                        now().toLocalDate()
                )
                .orElse(null);

        if (log == null) {
            return Optional.empty();
        }

        LogDetail detail = logDetailRepository
                .findByDetailIdAndNutritionLog_LogIdAndNutritionLog_User_Id(
                        detailId,
                        log.getLogId(),
                        userId
                )
                .orElse(null);

        if (detail == null) {
            return Optional.empty();
        }

        requireExpectedVersion(detail, request.getExpectedVersion());

        boolean sameFood = Objects.equals(
                detail.getFood().getFoodId(),
                request.getFoodId()
        );

        Food selectedFood = sameFood
                ? detail.getFood()
                : requireAvailableFood(request.getFoodId());

        OffsetDateTime acceptedAt =
                requireEditableNow(username, userId, log);

        detail.update(
                selectedFood,
                request.getMealType(),
                request.getWeightGrams(),
                request.getNote(),
                NutritionEntrySource.USER,
                acceptedAt
        );

        logDetailRepository.saveAndFlush(detail);

        return Optional.of(
                buildResponse(log, userId, log.getLogDate())
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<NutritionLogResponse> deleteDetail(
            String username,
            Integer detailId,
            Long expectedVersion
    ) {
        Long userId = requireUserId(username);
        validateDetailId(detailId);

        if (expectedVersion == null || expectedVersion < 0) {
            throw new IllegalArgumentException(
                    "Version phải là số lớn hơn hoặc bằng 0"
            );
        }

        NutritionLog log = nutritionLogRepository
                .findByUserIdAndLogDateForUpdate(
                        userId,
                        now().toLocalDate()
                )
                .orElse(null);

        if (log == null) {
            return Optional.empty();
        }

        LogDetail detail = logDetailRepository
                .findByDetailIdAndNutritionLog_LogIdAndNutritionLog_User_Id(
                        detailId,
                        log.getLogId(),
                        userId
                )
                .orElse(null);

        if (detail == null) {
            return Optional.empty();
        }

        requireExpectedVersion(detail, expectedVersion);
        requireEditableNow(username, userId, log);

        logDetailRepository.delete(detail);
        logDetailRepository.flush();

        return Optional.of(
                buildResponse(log, userId, log.getLogDate())
        );
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public Page<NutritionLogHistoryResponse> getLogHistory(
            String username,
            int page,
            int size
    ) {
        Long userId = requireUserId(username);

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Chỉ số trang phải lớn hơn hoặc bằng 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Kích thước trang phải từ 1 đến 100"
            );
        }

        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Vị trí phân trang vượt giới hạn hỗ trợ"
            );
        }

        Pageable pageable = PageRequest.of(page, size);

        Page<NutritionLog> logs = nutritionLogRepository
                .findByUser_IdOrderByLogDateDesc(
                        userId,
                        pageable
                );

        Map<Integer, LogDetailRepository.LogHistoryTotals> totalsByLog =
                new HashMap<>();

        if (!logs.isEmpty()) {
            List<Integer> logIds = logs.getContent()
                    .stream()
                    .map(NutritionLog::getLogId)
                    .toList();

            List<LogDetailRepository.LogHistoryTotals> summaries =
                    logDetailRepository.sumForHistory(
                            logIds,
                            userId
                    );

            for (LogDetailRepository.LogHistoryTotals summary : summaries) {
                totalsByLog.put(summary.getLogId(), summary);
            }
        }

        return logs.map(log -> {
            LogDetailRepository.LogHistoryTotals summary =
                    totalsByLog.get(log.getLogId());

            return new NutritionLogHistoryResponse(
                    log.getLogId(),
                    log.getLogDate(),
                    log.getCreatedAt(),
                    summary == null ? 0L : summary.getItemCount(),
                    toTotals(summary)
            );
        });
    }

    private Long requireUserId(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException(
                    "Không xác định được tài khoản đăng nhập"
            );
        }

        UserRepository.NutritionAccess user = userRepository
                .findNutritionAccessByUsername(username)
                .orElseThrow(() -> new AccessDeniedException(
                        "Không tìm thấy tài khoản đăng nhập"
                ));

        if (!user.getEnabled() || user.getLocked()) {
            throw new AccessDeniedException(
                    "Tài khoản chưa được xác minh hoặc đã bị khóa"
            );
        }

        return user.getId();
    }

    private void validateRequest(LogDetailRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu món ăn không được để trống"
            );
        }

        Set<ConstraintViolation<LogDetailRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.iterator().next().getMessage()
            );
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(
                nutritionClock.withZone(NutritionLog.VN_ZONE)
        ).truncatedTo(ChronoUnit.MICROS);
    }

    private NutritionLogResponse buildResponse(
            NutritionLog log,
            Long userId,
            LocalDate date
    ) {
        EnumMap<MealType, List<LogDetailResponse>> items =
                new EnumMap<>(MealType.class);

        EnumMap<MealType, NutritionTotalsResponse> mealTotals =
                new EnumMap<>(MealType.class);

        for (MealType mealType : MealType.values()) {
            items.put(mealType, new ArrayList<>());
            mealTotals.put(
                    mealType,
                    NutritionTotalsResponse.zero()
            );
        }

        NutritionTotalsResponse dailyTotals =
                NutritionTotalsResponse.zero();

        if (log != null) {
            List<LogDetail> details = logDetailRepository
                    .findAllByNutritionLog_LogIdAndNutritionLog_User_IdOrderByDetailIdAsc(
                            log.getLogId(),
                            userId
                    );

            for (LogDetail detail : details) {
                items.get(detail.getMealType())
                        .add(LogDetailResponse.from(detail));
            }

            dailyTotals = toTotals(
                    logDetailRepository.sumByLogIdAndUserId(
                            log.getLogId(),
                            userId
                    )
            );

            List<LogDetailRepository.MealTotals> summaries =
                    logDetailRepository.sumByMealTypeForLog(
                            log.getLogId(),
                            userId
                    );

            for (LogDetailRepository.MealTotals summary : summaries) {
                mealTotals.put(
                        summary.getMealType(),
                        toTotals(summary)
                );
            }
        }

        List<MealNutritionResponse> meals = new ArrayList<>();

        for (MealType mealType : MealType.values()) {
            meals.add(new MealNutritionResponse(
                    mealType,
                    items.get(mealType),
                    mealTotals.get(mealType)
            ));
        }

        return new NutritionLogResponse(
                log == null ? null : log.getLogId(),
                date,
                log == null ? null : log.getCreatedAt(),
                dailyTotals,
                meals
        );
    }

    private NutritionTotalsResponse toTotals(
            LogDetailRepository.NutritionTotals totals
    ) {
        if (totals == null) {
            return NutritionTotalsResponse.zero();
        }

        return new NutritionTotalsResponse(
                totals.getCalories(),
                totals.getProtein(),
                totals.getCarbs(),
                totals.getFat()
        );
    }
    private void validateDetailId(Integer detailId) {
        if (detailId == null || detailId <= 0) {
            throw new IllegalArgumentException(
                    "ID món ăn phải là số nguyên dương"
            );
        }
    }

    private void requireExpectedVersion(
            LogDetail detail,
            Long expectedVersion
    ) {
        if (!Objects.equals(detail.getVersion(), expectedVersion)) {
            throw new IllegalStateException(
                    "Món ăn đã thay đổi. Vui lòng tải lại nhật ký trước khi sửa hoặc xóa"
            );
        }
    }

    private Food requireAvailableFood(Integer foodId) {
        Integer categoryId = foodRepository
                .findCategoryIdByFoodId(foodId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Thực phẩm không tồn tại"
                ));

        FoodCategory category = foodCategoryRepository
                .findByIdForLogging(categoryId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nhóm thực phẩm không tồn tại"
                ));

        Food food = foodRepository
                .findByIdForLogging(foodId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Thực phẩm không tồn tại"
                ));

        if (!Objects.equals(
                food.getCategory().getCategoryId(),
                category.getCategoryId()
        )) {
            throw new IllegalStateException(
                    "Thực phẩm vừa đổi nhóm. Vui lòng tải lại và thử lại"
            );
        }

        if (!food.isAvailableForLogging()) {
            throw new IllegalArgumentException(
                    "Thực phẩm hoặc nhóm đã ngừng hoạt động"
            );
        }

        return food;
    }

    private OffsetDateTime requireEditableNow(
            String username,
            Long userId,
            NutritionLog log
    ) {
        if (!Objects.equals(userId, requireUserId(username))) {
            throw new AccessDeniedException(
                    "Thông tin tài khoản đã thay đổi"
            );
        }

        OffsetDateTime acceptedAt = now();
        log.requireEditableAt(acceptedAt);

        return acceptedAt;
    }
}