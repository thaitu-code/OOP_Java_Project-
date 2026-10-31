package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class WorkoutPageResponse {
    private List<WorkoutResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
