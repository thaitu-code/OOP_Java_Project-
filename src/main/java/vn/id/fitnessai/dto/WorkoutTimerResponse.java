package vn.id.fitnessai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class WorkoutTimerResponse {

    private WorkoutDetailResponse detail;
    private UUID timerToken;

    private OffsetDateTime lastRecordedAt;
    private OffsetDateTime lastHeartbeatAt;

    private long activeDurationMillis;
    private long restDurationMillis;

    private OffsetDateTime restStartedAt;
    private OffsetDateTime restEndsAt;
}
