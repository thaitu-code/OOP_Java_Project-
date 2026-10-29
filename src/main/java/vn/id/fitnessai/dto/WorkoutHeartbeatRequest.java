package vn.id.fitnessai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class WorkoutHeartbeatRequest {

    @NotNull(message = "Token timer không được để trống")
    private UUID timerToken;
}
