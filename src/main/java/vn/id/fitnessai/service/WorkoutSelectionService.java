package vn.id.fitnessai.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.WorkoutSelection;
import vn.id.fitnessai.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service @RequiredArgsConstructor
public class WorkoutSelectionService {
    private final AiAccess access;
    private final WorkoutService workouts;
    private final WorkoutRepository sessions;
    private final ExerciseRepository exercises;
    private final WorkoutSelectionRepository receipts;
    private final Validator validator;

    @Transactional
    public WorkoutResponse create(String username, WorkoutSelectionRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) throw new IllegalArgumentException("Chọn từ 1 đến 50 bài hợp lệ và gửi kèm mã yêu cầu");
        if (request.items().stream().map(WorkoutDetailRequest::getExerciseId).distinct().count() != request.items().size())
            throw new IllegalArgumentException("Danh sách có bài tập trùng nhau");
        var user = access.lock(username); // Serializes all creations/replays for this account.
        String canonical = request.items().stream().map(i -> i.getExerciseId()+":"+i.getLevelId()+":"+i.getPlannedSets()+":"+i.getPlannedReps()).collect(java.util.stream.Collectors.joining(";"));
        String fingerprint;
        try { fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var previous = receipts.findByUser_IdAndRequestId(user.getId(), request.requestId());
        if (previous.isPresent()) {
            if (!fingerprint.equals(previous.get().getFingerprint())) throw new IllegalStateException("Mã yêu cầu đã được dùng cho danh sách khác");
            return workouts.getWorkout(username, previous.get().getWorkout().getWorkoutId()).orElseThrow();
        }
        var result = workouts.createWorkout(username);
        // Stable lock order across submissions; display order is restored by insertion order below.
        for (Integer id : request.items().stream().map(WorkoutDetailRequest::getExerciseId).sorted().toList())
            exercises.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Bài tập không còn tồn tại. Hãy chọn lại."));
        for (var item : request.items()) workouts.addExerciseToWorkout(username, result.getWorkoutId(), item)
                .orElseThrow(() -> new IllegalArgumentException("Bài tập không còn tồn tại. Hãy chọn lại."));
        var receipt = new WorkoutSelection();receipt.setUser(user);receipt.setRequestId(request.requestId());receipt.setFingerprint(fingerprint);
        receipt.setWorkout(sessions.getReferenceById(result.getWorkoutId()));receipts.save(receipt);
        return workouts.getWorkout(username, result.getWorkoutId()).orElseThrow();
    }
}
