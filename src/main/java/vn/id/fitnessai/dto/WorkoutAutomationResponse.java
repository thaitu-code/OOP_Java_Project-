package vn.id.fitnessai.dto;

import java.time.OffsetDateTime;

public record WorkoutAutomationResponse(Integer levelId, String levelName, int plannedSets, Integer reps,
        int secondsPerSet, int estimatedSeconds, long minimumMillis, int currentSet,
        long elapsedInSetMillis, long phaseRemainingMillis, OffsetDateTime phaseEndsAt,
        boolean canFinishEarly, boolean fullTargetReached, String mediaUrl) { }
