package vn.id.fitnessai.dto;

import java.util.List;

/** Một snapshot đọc cho màn hình buổi đang tập; timer không mang token điều khiển. */
public record WorkoutSessionStateResponse(WorkoutResponse workout,
        List<WorkoutDetailResponse> details, WorkoutTimerResponse timer) {
}
