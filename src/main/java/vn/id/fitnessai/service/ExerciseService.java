package vn.id.fitnessai.service;

import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.ExerciseRequest;
import vn.id.fitnessai.dto.ExerciseResponse;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.entity.ExerciseCategory;
import vn.id.fitnessai.repository.ExerciseCategoryRepository;
import vn.id.fitnessai.repository.ExerciseRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository; //Tiêm lớp repository của exercise lên
    private final ExerciseCategoryRepository exerciseCategoryRepository;    //Tiêm lớp repository của exerciseCategory lên
    private final Validator validator;  //Bắt các lỗi trong exerciseRequest

    @Transactional
    public ExerciseResponse createExercise(ExerciseRequest request){

        if (request == null){
            throw new IllegalArgumentException("Dữ liệu tạo bài tập không được để trống");
        }

        Set<ConstraintViolation<ExerciseRequest>> violations = validator.validate(request);

        if (!violations.isEmpty()){
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }

        Optional<ExerciseCategory> categoryOptional = exerciseCategoryRepository.findById(request.getCategoryId());

        if (categoryOptional.isEmpty()){
            throw new IllegalArgumentException("Nhóm bài tập không tồn tại");
        }

        ExerciseCategory category = categoryOptional.get();

        if (!category.isActive()){
            throw new IllegalArgumentException("Không thể gán bài tập mới vào nhóm đã ngừng hoạt động");
        }

        //Tạo exercise với các giá trị NotNull
        Exercise exercise = new Exercise(request.getExerciseName().strip(),
                                        request.getMetValue(), request.getMinDurationMinutes(), category);

        //
        exercise.setDescription(request.getDescription());
        exercise.setRecommendedSets(request.getRecommendedSets());
        exercise.setRecommendedReps(request.getRecommendedReps());
        exercise.setAiVideoUrl(request.getAiVideoUrl());

        //Lưu xuống repository
        Exercise savedExercise = exerciseRepository.save(exercise);

        return new ExerciseResponse(
                savedExercise.getExerciseId(),
                savedExercise.getExerciseName(),
                savedExercise.getDescription(),
                savedExercise.getMetValue(),
                savedExercise.getMinDurationMinutes(),
                savedExercise.getRecommendedSets(),
                savedExercise.getRecommendedReps(),
                savedExercise.getAiVideoUrl(),
                savedExercise.getCategory().getCategoryId(),
                savedExercise.getCategory().getCategoryName()
        );
    }
}
