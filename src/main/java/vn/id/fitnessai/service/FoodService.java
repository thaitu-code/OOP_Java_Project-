package vn.id.fitnessai.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.FoodRequest;
import vn.id.fitnessai.dto.FoodResponse;
import vn.id.fitnessai.entity.Food;
import vn.id.fitnessai.entity.FoodCategory;
import vn.id.fitnessai.repository.FoodCategoryRepository;
import vn.id.fitnessai.repository.FoodRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FoodService {

    private static final ZoneId VN_ZONE =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final FoodRepository foodRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final Validator validator;

    @Transactional
    public FoodResponse createFood(FoodRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu tạo thực phẩm không được để trống"
            );
        }

        Set<ConstraintViolation<FoodRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.iterator().next().getMessage()
            );
        }

        Optional<FoodCategory> categoryOptional =
                foodCategoryRepository.findByIdForUpdate(
                        request.getCategoryId()
                );

        if (categoryOptional.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nhóm thực phẩm không tồn tại"
            );
        }

        FoodCategory category = categoryOptional.get();

        if (!category.isActive()) {
            throw new IllegalArgumentException(
                    "Nhóm thực phẩm đã ngừng hoạt động"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

        Food food = new Food(
                category,
                request.getFoodName(),
                request.getDescription(),
                request.getSource(),
                request.getImageUrl(),
                request.getCaloriesPer100g(),
                request.getProteinPer100g(),
                request.getCarbsPer100g(),
                request.getFatPer100g(),
                now
        );

        if (foodRepository.existsByFoodNameKey(
                food.getFoodNameKey()
        )) {
            throw new IllegalArgumentException(
                    "Tên thực phẩm đã tồn tại"
            );
        }

        Food savedFood = foodRepository.save(food);

        return FoodResponse.from(savedFood);
    }

    @Transactional(readOnly = true)
    public Page<FoodResponse> getActiveFoods(
            String keyword,
            Integer categoryId,
            int page,
            int size
    ) {
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

        if (categoryId != null && categoryId <= 0) {
            throw new IllegalArgumentException(
                    "ID nhóm thực phẩm phải là số nguyên dương"
            );
        }

        String normalizedKeyword = keyword == null
                ? ""
                : keyword.replaceAll("(?U)\\s+", " ").strip();

        if (normalizedKeyword.length() > 150) {
            throw new IllegalArgumentException(
                    "Từ khóa tìm kiếm không được vượt quá 150 ký tự"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("foodName", "foodId")
        );

        Page<Food> foods;

        if (categoryId == null) {
            foods = foodRepository
                    .findByActiveTrueAndCategory_ActiveTrueAndFoodNameContainingIgnoreCase(
                            normalizedKeyword,
                            pageable
                    );
        } else {
            foods = foodRepository
                    .findByCategory_CategoryIdAndActiveTrueAndCategory_ActiveTrueAndFoodNameContainingIgnoreCase(
                            categoryId,
                            normalizedKeyword,
                            pageable
                    );
        }

        return foods.map(FoodResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<FoodResponse> getActiveFoodById(
            Integer foodId
    ) {
        if (foodId == null || foodId <= 0) {
            throw new IllegalArgumentException(
                    "ID thực phẩm phải là số nguyên dương"
            );
        }

        Optional<Food> foodOptional =
                foodRepository.findById(foodId);

        if (foodOptional.isEmpty()) {
            return Optional.empty();
        }

        Food food = foodOptional.get();

        if (!food.isAvailableForLogging()) {
            return Optional.empty();
        }

        return Optional.of(FoodResponse.from(food));
    }
    @Transactional(readOnly = true)
    public Page<FoodResponse> getAllFoods(
            String keyword,
            int page,
            int size
    ) {
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

        String normalizedKeyword = keyword == null
                ? ""
                : keyword.replaceAll("(?U)\\s+", " ").strip();

        if (normalizedKeyword.length() > 150) {
            throw new IllegalArgumentException(
                    "Từ khóa tìm kiếm không được vượt quá 150 ký tự"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("foodName", "foodId")
        );

        Page<Food> foods =
                foodRepository.findByFoodNameContainingIgnoreCase(
                        normalizedKeyword,
                        pageable
                );

        return foods.map(FoodResponse::from);
    }

    @Transactional
    public Optional<FoodResponse> updateFood(
            Integer foodId,
            FoodRequest request
    ) {
        if (foodId == null || foodId <= 0) {
            throw new IllegalArgumentException(
                    "ID thực phẩm phải là số nguyên dương"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu cập nhật thực phẩm không được để trống"
            );
        }

        Set<ConstraintViolation<FoodRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.iterator().next().getMessage()
            );
        }

        Optional<FoodCategory> categoryOptional =
                foodCategoryRepository.findByIdForUpdate(
                        request.getCategoryId()
                );

        if (categoryOptional.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nhóm thực phẩm không tồn tại"
            );
        }

        FoodCategory category = categoryOptional.get();

        Optional<Food> foodOptional =
                foodRepository.findByIdForUpdate(foodId);

        if (foodOptional.isEmpty()) {
            return Optional.empty();
        }

        Food food = foodOptional.get();

        boolean sameCategory = category.getCategoryId().equals(
                food.getCategory().getCategoryId()
        );

        if (!sameCategory && !category.isActive()) {
            throw new IllegalArgumentException(
                    "Không thể chuyển sang nhóm thực phẩm đã ngừng hoạt động"
            );
        }

        String normalizedName = request.getFoodName()
                .replaceAll("(?U)\\s+", " ")
                .strip();

        String nameKey = normalizedName.toLowerCase(Locale.ROOT);

        if (foodRepository.existsByFoodNameKeyAndFoodIdNot(
                nameKey,
                foodId
        )) {
            throw new IllegalArgumentException(
                    "Tên thực phẩm đã tồn tại"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

        food.update(
                category,
                normalizedName,
                request.getDescription(),
                request.getSource(),
                request.getImageUrl(),
                request.getCaloriesPer100g(),
                request.getProteinPer100g(),
                request.getCarbsPer100g(),
                request.getFatPer100g(),
                now
        );

        Food savedFood = foodRepository.save(food);

        return Optional.of(FoodResponse.from(savedFood));
    }

    @Transactional
    public Optional<FoodResponse> updateFoodStatus(
            Integer foodId,
            boolean active
    ) {
        if (foodId == null || foodId <= 0) {
            throw new IllegalArgumentException(
                    "ID thực phẩm phải là số nguyên dương"
            );
        }

        Optional<Food> foodOptional =
                foodRepository.findByIdForUpdate(foodId);

        if (foodOptional.isEmpty()) {
            return Optional.empty();
        }

        Food food = foodOptional.get();

        if (food.isActive() != active) {
            OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

            food.setActive(active, now);
        }

        Food savedFood = foodRepository.save(food);

        return Optional.of(FoodResponse.from(savedFood));
    }
}