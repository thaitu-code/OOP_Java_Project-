package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WorkoutRestApprovalResponse {
    private Integer detailId;
    private Integer workoutId;
    private Integer approvedRestSecondsSnapshot;
}
