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
                savedExercise.isActive(),
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

    @Transactional
    public List<ExerciseResponse> getAllExercises(){

        List<Exercise> exercises = exerciseRepository.findAll();
        List<ExerciseResponse> responses = new ArrayList<>();

        for (Exercise exercise : exercises){
            ExerciseResponse response = new ExerciseResponse(
                    exercise.getExerciseId(),
                    exercise.getExerciseName(),
                    exercise.isActive(),
                    exercise.getDescription(),
                    exercise.getMetValue(),
                    exercise.getMinDurationMinutes(),
                    exercise.getRecommendedSets(),
                    exercise.getRecommendedReps(),
                    exercise.getAiVideoUrl(),
                    exercise.getCategory().getCategoryId(),
                    exercise.getCategory().getCategoryName()
            );

            responses.add(response);
        }

        return responses;
    }

    @Transactional
    public Optional<ExerciseResponse> getExerciseById(Integer exerciseId){
        if (exerciseId == null || exerciseId <= 0){
            throw new IllegalArgumentException("ID bài tập phải là số nguyên dương");
        }

        Optional<Exercise> exerciseOptional = exerciseRepository.findById(exerciseId);

        if (exerciseOptional.isEmpty()){
            return Optional.empty();
        }

        Exercise exercise = exerciseOptional.get();

        ExerciseResponse response = new ExerciseResponse(
                exercise.getExerciseId(),
                exercise.getExerciseName(),
                exercise.isActive(),
                exercise.getDescription(),
                exercise.getMetValue(),
                exercise.getMinDurationMinutes(),
                exercise.getRecommendedSets(),
                exercise.getRecommendedReps(),
                exercise.getAiVideoUrl(),
                exercise.getCategory().getCategoryId(),
                exercise.getCategory().getCategoryName()
        );

        return Optional.of(response);
    }

    @Transactional
    public Optional<ExerciseResponse> updateExercise(Integer exerciseId, ExerciseRequest  request){

        if (exerciseId == null || exerciseId <= 0){
            throw new IllegalArgumentException("ID bài tập phải là số nguyên dương");
        }

        if (request == null){
            throw new IllegalArgumentException("Dữ liệu bài tập không được để trống");
        }

        //Check lỗi trong request
        Set<ConstraintViolation<ExerciseRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()){
            throw new IllegalArgumentException(violations.iterator().next().getMessage());
        }
        Optional<Exercise> exerciseOptional = exerciseRepository.findById(exerciseId);

        if (exerciseOptional.isEmpty()){
            return Optional.empty();
        }

        //Exercise đang lưu
        Exercise exercise = exerciseOptional.get();

        Optional<ExerciseCategory> categoryOptional = exerciseCategoryRepository.findById(request.getCategoryId());

        if (categoryOptional.isEmpty()){
            throw new IllegalArgumentException("Nhóm bài tập không tồn tại");
        }

        ExerciseCategory category = categoryOptional.get();

        boolean categoryChanged = !Objects.equals(exercise.getCategory().getCategoryId(), category.getCategoryId());

        //Nếu nhóm bài tập mới khác nhóm cũ nhưng không hoạt động
        if (categoryChanged && !category.isActive()){
            throw new IllegalArgumentException("Không thể chuyển sang nhóm đã ngừng hoạt động");
        }

        //Cập nhật giá trị
        exercise.setExerciseName(request.getExerciseName().strip());
        exercise.setDescription(request.getDescription());
        exercise.setMetValue(request.getMetValue());
        exercise.setMinDurationMinutes(request.getMinDurationMinutes());
        exercise.setRecommendedSets(request.getRecommendedSets());
        exercise.setRecommendedReps(request.getRecommendedReps());
        exercise.setAiVideoUrl(request.getAiVideoUrl());
        exercise.setCategory(category);

        //Lưu xuống db
        Exercise savedExercise = exerciseRepository.save(exercise);

        //Chuyển sang DTO response
        ExerciseResponse response = new ExerciseResponse(
                savedExercise.getExerciseId(),
                savedExercise.getExerciseName(),
                savedExercise.isActive(),
                savedExercise.getDescription(),
                savedExercise.getMetValue(),
                savedExercise.getMinDurationMinutes(),
                savedExercise.getRecommendedSets(),
                savedExercise.getRecommendedReps(),
                savedExercise.getAiVideoUrl(),
                savedExercise.getCategory().getCategoryId(),
                savedExercise.getCategory().getCategoryName()
        );

        return Optional.of(response);
    }

    @Transactional
    public Optional<ExerciseResponse> updateExerciseStatus(Integer exerciseId, boolean active){

        if (exerciseId == null || exerciseId <= 0){
            throw new IllegalArgumentException("ID bài tập phải là số nguyên dương");
        }

        Optional<Exercise> exerciseOptional = exerciseRepository.findById(exerciseId);
        if (exerciseOptional.isEmpty()){
            return Optional.empty();
        }

        Exercise exercise = exerciseOptional.get();
        exercise.setActive(active);

        Exercise savedExercise = exerciseRepository.save(exercise);

        ExerciseResponse response = new ExerciseResponse(
                savedExercise.getExerciseId(),
                savedExercise.getExerciseName(),
                savedExercise.isActive(),
                savedExercise.getDescription(),
                savedExercise.getMetValue(),
                savedExercise.getMinDurationMinutes(),
                savedExercise.getRecommendedSets(),
                savedExercise.getRecommendedReps(),
                savedExercise.getAiVideoUrl(),
                savedExercise.getCategory().getCategoryId(),
                savedExercise.getCategory().getCategoryName()
        );

        return Optional.of(response);
    }
}
