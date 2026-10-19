package vn.id.fitnessai.service;


import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.ExerciseCategoryRequest;
import vn.id.fitnessai.dto.ExerciseCategoryResponse;
import vn.id.fitnessai.entity.ExerciseCategory;
import vn.id.fitnessai.repository.ExerciseCategoryRepository;

import java.util.*;

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

    @Transactional
    public List<ExerciseCategoryResponse> getAllCategories(){
        List<ExerciseCategory> categories = exerciseCategoryRepository.findAll();

        List<ExerciseCategoryResponse> responses = new ArrayList<>();

        for(ExerciseCategory category : categories){
            ExerciseCategoryResponse response = new ExerciseCategoryResponse(category.getCategoryId(), category.getCategoryName());

            responses.add(response);
        }

        return responses;
    }

    @Transactional
    public Optional<ExerciseCategoryResponse> getCategoryById(Integer categoryId){
        if (categoryId == null || categoryId <= 0){
            throw new IllegalArgumentException("ID nhóm bài tập phải là số nguyên dương");
        }
        //Optional trả về empty nếu gặp null
        Optional<ExerciseCategory> category = exerciseCategoryRepository.findById(categoryId);

        if (category.isEmpty()){
            return Optional.empty();
        }

        //Chuyển từ optional sang ExerciseCategory để tạo Responese
        ExerciseCategory foundCategory = category.get();

        ExerciseCategoryResponse response = new ExerciseCategoryResponse(foundCategory.getCategoryId(), foundCategory.getCategoryName());

        return Optional.of(response);
    }

    @Transactional
    public Optional<ExerciseCategoryResponse> updateCategory(Integer categoryId, ExerciseCategoryRequest request){

        //Check TÊN và ID hợp lệ
        if (categoryId == null || categoryId <= 0){
            throw new IllegalArgumentException("ID nhóm bài tập phải là số nguyên dương");
        }

        if (request == null){
            throw new IllegalArgumentException("Dữ liệu cập nhật nhóm không được để trống");
        }

        String rawName = request.getCategoryName();

        if (rawName == null || rawName.isBlank()){
            throw new IllegalArgumentException("Tên nhóm không được để trống");
        }

        if (rawName.length() > 100){
            throw new IllegalArgumentException("Tên nhóm không được vượt quá 100 kí tự");
        }

        String categoryName = rawName.strip();

        //Tìm nhóm hiện có

        Optional<ExerciseCategory> category = exerciseCategoryRepository.findById(categoryId);  //Lấy danh mục theo ID

        //Ko tìm đc thì trả về rỗng
        if (category.isEmpty()){
            return Optional.empty();
        }

        //Check tên mới có trùng với nhóm khác không
        if (exerciseCategoryRepository.existsByCategoryNameAndCategoryIdNot(categoryName, categoryId)){
            throw new IllegalArgumentException("Tên nhóm đã tồn tại");
        }

        //Lưu category với tên mới vào Repository
        ExerciseCategory foundCategory = category.get();
        foundCategory.setCategoryName(categoryName);
        ExerciseCategory savedCategory = exerciseCategoryRepository.save(foundCategory);

        return Optional.of(new ExerciseCategoryResponse(savedCategory.getCategoryId(), savedCategory.getCategoryName()));
    }
}
