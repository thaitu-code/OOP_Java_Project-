package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.id.fitnessai.repository.WorkoutDetailRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkoutTimerTimeoutScheduler {
    private final WorkoutDetailRepository workoutDetailRepository;
    private final WorkoutTrackingService workoutTrackingService;

    @Value("${fitness.workout.heartbeat-timeout-seconds:30}")
    private long timeoutSeconds;

    @Scheduled(fixedDelayString = "${fitness.workout.timeout-scan-interval-millis:10000}",
            initialDelayString = "${fitness.workout.timeout-scan-interval-millis:10000}")
    public void freezeExpiredTimers() {
        if (timeoutSeconds <= 0) {
            log.error("fitness.workout.heartbeat-timeout-seconds phải lớn hơn 0");
            return;
        }
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minus(timeout);
        for (Integer workoutId : workoutDetailRepository.findExpiredWorkoutIds(cutoff, PageRequest.of(0, 100))) {
            try {
                workoutTrackingService.freezeExpiredWorkout(workoutId, timeout);
            } catch (RuntimeException exception) {
                log.error("Không thể đóng băng timer của buổi {}", workoutId, exception);
            }
        }
    }
}
