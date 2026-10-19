package vn.id.fitnessai.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.ExerciseCategoryRequest;
import vn.id.fitnessai.dto.ExerciseCategoryResponse;
import vn.id.fitnessai.entity.ExerciseCategory;
import vn.id.fitnessai.repository.ExerciseCategoryRepository;

@Service
@RequiredArgsConstructor
public class ExerciseCategoryService {
    private final ExerciseCategoryRepository exerciseCategoryRepository;

    @Transactional //giúp roleback data khi ném Exception ra khỏi phương thức
    public ExerciseCategoryResponse createCategory(ExerciseCategoryRequest request){
        if (request == null){
            throw new IllegalArgumentException("Dữ liệu tạo nhóm không được để trống");
        }

        String rawName = request.getCategoryName();

        if(rawName == null || rawName.isBlank()){
            throw new IllegalArgumentException("Tên nhóm không được để trống");
        }

        if (rawName.length() > 100){
            throw new IllegalArgumentException("Tên nhóm không được vượt quá 100 kí tự");
        }

        String categoryName = rawName.strip();

        if (exerciseCategoryRepository.existsByCategoryName(categoryName)){
            throw new IllegalArgumentException("Tên nhóm đã tồn tại");
        }

        ExerciseCategory category = new ExerciseCategory(categoryName);
        ExerciseCategory savedCategory = exerciseCategoryRepository.save(category);

        return new ExerciseCategoryResponse(savedCategory.getCategoryId(), savedCategory.getCategoryName());
    }
}
