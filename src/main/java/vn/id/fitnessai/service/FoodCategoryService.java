package vn.id.fitnessai.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.FoodCategoryRequest;
import vn.id.fitnessai.dto.FoodCategoryResponse;
import vn.id.fitnessai.entity.FoodCategory;
import vn.id.fitnessai.repository.FoodCategoryRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FoodCategoryService {
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final FoodCategoryRepository foodCategoryRepository;
    private final Validator validator;

    @Transactional
    public FoodCategoryResponse createCategory(FoodCategoryRequest request){
        if (request == null){
            throw new IllegalArgumentException("Dữ liệu tạo nhóm thực phẩm không được để trống");
        }

        Set<ConstraintViolation<FoodCategoryRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()){
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }

        OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

        FoodCategory category = new FoodCategory(request.getCategoryName(), request.getDescription(), now);

        if (foodCategoryRepository.existsByCategoryNameKey(category.getCategoryNameKey())){
            throw new IllegalArgumentException("Tên nhóm thực phẩm đã tồn tại");
        }

        FoodCategory savedCategory = foodCategoryRepository.save(category);

        return new FoodCategoryResponse(
                savedCategory.getCategoryId(),
                savedCategory.getCategoryName(),
                savedCategory.getDescription(),
                savedCategory.isActive(),
                savedCategory.getCreatedAt(),
                savedCategory.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<FoodCategoryResponse> getActiveCategories() {
        List<FoodCategory> categories =
                foodCategoryRepository.findAllByActiveTrueOrderByCategoryNameAsc();

        List<FoodCategoryResponse> responses = new ArrayList<>();

        for (FoodCategory category : categories) {
            FoodCategoryResponse response = new FoodCategoryResponse(
                    category.getCategoryId(),
                    category.getCategoryName(),
                    category.getDescription(),
                    category.isActive(),
                    category.getCreatedAt(),
                    category.getUpdatedAt()
            );

            responses.add(response);
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<FoodCategoryResponse> getAllCategories() {
        List<FoodCategory> categories =
                foodCategoryRepository.findAll(
                        Sort.by("categoryName", "categoryId")
                );

        List<FoodCategoryResponse> responses = new ArrayList<>();

        for (FoodCategory category : categories) {
            FoodCategoryResponse response = new FoodCategoryResponse(
                    category.getCategoryId(),
                    category.getCategoryName(),
                    category.getDescription(),
                    category.isActive(),
                    category.getCreatedAt(),
                    category.getUpdatedAt()
            );

            responses.add(response);
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public Optional<FoodCategoryResponse> getActiveCategoryById(
            Integer categoryId
    ) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException(
                    "ID nhóm thực phẩm phải là số nguyên dương"
            );
        }

        Optional<FoodCategory> categoryOptional =
                foodCategoryRepository.findById(categoryId);

        if (categoryOptional.isEmpty()) {
            return Optional.empty();
        }

        FoodCategory category = categoryOptional.get();

        if (!category.isActive()) {
            return Optional.empty();
        }

        FoodCategoryResponse response = new FoodCategoryResponse(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getDescription(),
                category.isActive(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );

        return Optional.of(response);
    }

    @Transactional
    public Optional<FoodCategoryResponse> updateCategory(
            Integer categoryId,
            FoodCategoryRequest request
    ) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException(
                    "ID nhóm thực phẩm phải là số nguyên dương"
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Dữ liệu cập nhật nhóm thực phẩm không được để trống"
            );
        }

        Set<ConstraintViolation<FoodCategoryRequest>> violations =
                validator.validate(request);

        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(
                    violations.iterator().next().getMessage()
            );
        }

        Optional<FoodCategory> categoryOptional =
                foodCategoryRepository.findByIdForUpdate(categoryId);

        if (categoryOptional.isEmpty()) {
            return Optional.empty();
        }

        FoodCategory category = categoryOptional.get();

        OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

        FoodCategory proposedCategory = new FoodCategory(
                request.getCategoryName(),
                request.getDescription(),
                now
        );

        boolean nameExists =
                foodCategoryRepository.existsByCategoryNameKeyAndCategoryIdNot(
                        proposedCategory.getCategoryNameKey(),
                        categoryId
                );

        if (nameExists) {
            throw new IllegalArgumentException(
                    "Tên nhóm thực phẩm đã tồn tại"
            );
        }

        category.update(
                proposedCategory.getCategoryName(),
                proposedCategory.getDescription(),
                now
        );

        FoodCategory savedCategory =
                foodCategoryRepository.save(category);

        FoodCategoryResponse response = new FoodCategoryResponse(
                savedCategory.getCategoryId(),
                savedCategory.getCategoryName(),
                savedCategory.getDescription(),
                savedCategory.isActive(),
                savedCategory.getCreatedAt(),
                savedCategory.getUpdatedAt()
        );

        return Optional.of(response);
    }

    @Transactional
    public Optional<FoodCategoryResponse> updateCategoryStatus(
            Integer categoryId,
            boolean active
    ) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException(
                    "ID nhóm thực phẩm phải là số nguyên dương"
            );
        }

        Optional<FoodCategory> categoryOptional =
                foodCategoryRepository.findByIdForUpdate(categoryId);

        if (categoryOptional.isEmpty()) {
            return Optional.empty();
        }

        FoodCategory category = categoryOptional.get();

        if (category.isActive() != active) {
            OffsetDateTime now = OffsetDateTime.now(VN_ZONE);

            category.setActive(active, now);
        }

        FoodCategory savedCategory =
                foodCategoryRepository.save(category);

        FoodCategoryResponse response = new FoodCategoryResponse(
                savedCategory.getCategoryId(),
                savedCategory.getCategoryName(),
                savedCategory.getDescription(),
                savedCategory.isActive(),
                savedCategory.getCreatedAt(),
                savedCategory.getUpdatedAt()
        );

        return Optional.of(response);
    }
}
