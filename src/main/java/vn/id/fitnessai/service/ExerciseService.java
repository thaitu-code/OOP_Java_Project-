package vn.id.fitnessai.service;

import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.id.fitnessai.dto.ExerciseRequest;
import vn.id.fitnessai.dto.ExerciseResponse;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.entity.ExerciseCategory;
import vn.id.fitnessai.enums.WorkoutStatus;
import vn.id.fitnessai.repository.ExerciseCategoryRepository;
import vn.id.fitnessai.repository.ExerciseRepository;
import vn.id.fitnessai.repository.WorkoutDetailRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRepository exerciseRepository; //Tiêm lớp repository của exercise lên
    private final ExerciseCategoryRepository exerciseCategoryRepository;    //Tiêm lớp repository của exerciseCategory lên
    private final WorkoutDetailRepository workoutDetailRepository;
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
        String mediaUrl = ExerciseMediaLinks.normalize(request.getMediaUrl());

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
        exercise.setMediaUrl(mediaUrl);

        //Lưu xuống repository
        Exercise savedExercise = exerciseRepository.save(exercise);

        return ExerciseResponse.from(savedExercise);
    }

    @Transactional(readOnly = true)
    public List<ExerciseResponse> getAllExercises(){

        List<Exercise> exercises = exerciseRepository.findAll();
        List<ExerciseResponse> responses = new ArrayList<>();

        for (Exercise exercise : exercises){
            ExerciseResponse response = ExerciseResponse.from(exercise);

            responses.add(response);
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public Optional<ExerciseResponse> getExerciseById(Integer exerciseId){
        if (exerciseId == null || exerciseId <= 0){
            throw new IllegalArgumentException("ID bài tập phải là số nguyên dương");
        }

        Optional<Exercise> exerciseOptional = exerciseRepository.findById(exerciseId);

        if (exerciseOptional.isEmpty()){
            return Optional.empty();
        }

        Exercise exercise = exerciseOptional.get();

        ExerciseResponse response = ExerciseResponse.from(exercise);

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
        String mediaUrl = ExerciseMediaLinks.normalize(request.getMediaUrl());
        Optional<Exercise> exerciseOptional = exerciseRepository.findByIdForUpdate(exerciseId);

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
        // Request cũ không có mediaUrl giữ media hiện tại; chuỗi rỗng chủ động gỡ liên kết.
        if (request.getMediaUrl() != null) exercise.setMediaUrl(mediaUrl);
        exercise.setCategory(category);

        //Lưu xuống db
        Exercise savedExercise = exerciseRepository.save(exercise);

        //Chuyển sang DTO response
        ExerciseResponse response = ExerciseResponse.from(savedExercise);

        return Optional.of(response);
    }

    @Transactional
    public Optional<ExerciseResponse> updateExerciseStatus(Integer exerciseId, boolean active){

        if (exerciseId == null || exerciseId <= 0){
            throw new IllegalArgumentException("ID bài tập phải là số nguyên dương");
        }

        Optional<Exercise> exerciseOptional = exerciseRepository.findByIdForUpdate(exerciseId);
        if (exerciseOptional.isEmpty()){
            return Optional.empty();
        }

        Exercise exercise = exerciseOptional.get();
        exercise.setActive(active);

        Exercise savedExercise = exerciseRepository.save(exercise);

        ExerciseResponse response = ExerciseResponse.from(savedExercise);

        return Optional.of(response);
    }

    @Transactional
    public boolean deleteExercise(Integer exerciseId){

        if (exerciseId == null || exerciseId <=0){
            throw new IllegalArgumentException("ID bài tập phải là số nguyên dương");
        }

        Optional<Exercise> exerciseOptional = exerciseRepository.findByIdForUpdate(exerciseId);

        if (exerciseOptional.isEmpty()){
            return false;
        }

        boolean usedInActiveWorkout = workoutDetailRepository.existsByExercise_ExerciseIdAndWorkout_Status(exerciseId, WorkoutStatus.IN_PROGRESS);

        if (usedInActiveWorkout){
            throw new IllegalStateException("Không thể xóa bài tập đang thuộc buổi tập chưa kết thúc");
        }
        if (exerciseRepository.isUsedByAiPlans(exerciseId)) {
            throw new IllegalStateException("Bài tập đang được lịch AI tham chiếu. Hãy ngừng hoạt động để giữ lịch sử và đề xuất bài thay thế.");
        }

        // Hibernate schema update may leave an existing FK without ON DELETE SET NULL.
        // Detach closed history explicitly in this transaction before deleting the master.
        workoutDetailRepository.detachExerciseReferences(exerciseId);
        exerciseRepository.delete(exerciseOptional.get());
        exerciseRepository.flush();

        return true;
    }
}
