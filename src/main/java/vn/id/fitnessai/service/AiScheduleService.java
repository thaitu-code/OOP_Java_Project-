package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.time.*;
import java.util.*;

/** One effective schedule resolver shared by calendar, workouts and streak reconciliation. */
@Service @RequiredArgsConstructor
public class AiScheduleService {
    private final AiRecommendationRepository recommendations;
    private final AiRecommendedExerciseRepository exercises;
    private final WorkoutDetailRepository details;

    public static LocalDate start(AiRecommendation plan) {
        return plan.getWorkoutStartDate() != null ? plan.getWorkoutStartDate()
                : plan.getCreatedAt().atZoneSameInstant(NutritionLog.VN_ZONE).toLocalDate()
                    .with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    private boolean accepted(AiRecommendation p) {
        return Set.of("APPLIED", "SCHEDULED", "REPLACED").contains(p.getStatus());
    }

    @Transactional(readOnly = true)
    public Optional<AiRecommendation> effective(Long userId, LocalDate date) {
        List<AiRecommendation> all = recommendations.findByProfile_User_IdAndTypeOrderByIdDesc(userId, "WORKOUT_PLAN");
        Optional<AiRecommendation> base = all.stream().filter(this::accepted)
                .filter(p -> p.getWorkoutOverrideDate() == null && !start(p).isAfter(date))
                .filter(p -> p.getEffectiveUntil() == null || !date.isAfter(p.getEffectiveUntil()))
                .max(Comparator.comparing(AiScheduleService::start).thenComparing(AiRecommendation::getId));
        if (base.isEmpty()) return Optional.empty();
        return all.stream().filter(p -> "APPLIED".equals(p.getStatus()) && date.equals(p.getWorkoutOverrideDate())
                        && Objects.equals(p.getParentRecommendationId(), base.get().getId()))
                .max(Comparator.comparing(AiRecommendation::getId)).or(() -> base);
    }

    public boolean explicitRest(AiRecommendation plan, LocalDate date) {
        return plan.getRestDays() != null && Arrays.stream(plan.getRestDays().split(","))
                .anyMatch(s -> s.equals(String.valueOf(date.getDayOfWeek().getValue())));
    }

    @Transactional(readOnly = true)
    public boolean isRestDay(Long userId, LocalDate date) {
        return effective(userId, date).map(p -> explicitRest(p, date)).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<LocalDate> firstDate(Long userId) {
        return recommendations.findByProfile_User_IdAndTypeOrderByIdDesc(userId, "WORKOUT_PLAN").stream()
                .filter(this::accepted).filter(p -> p.getWorkoutOverrideDate() == null)
                .filter(p -> p.getEffectiveUntil() == null || !p.getEffectiveUntil().isBefore(start(p)))
                .map(AiScheduleService::start).min(LocalDate::compareTo);
    }

    @Transactional(readOnly = true)
    public List<AiRecommendedExercise> entries(Long userId, LocalDate date) {
        return effective(userId, date).map(p -> exercises.findByRecommendation_IdOrderByDayOfWeekAscPositionAsc(p.getId())
                .stream().filter(e -> e.getDayOfWeek() == date.getDayOfWeek().getValue()).toList()).orElse(List.of());
    }

    public void requireCanRest(Long userId, LocalDate date, LocalDate today) {
        if (date.isBefore(today)) throw new IllegalArgumentException("Không được sửa ngày đã chốt");
        if (date.equals(today) && details.existsByWorkout_User_IdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                userId, today.atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime(),
                today.plusDays(1).atStartOfDay(NutritionLog.VN_ZONE).toOffsetDateTime())) {
            throw new IllegalArgumentException("Hôm nay đã bắt đầu tập; chỉ được đổi thành ngày nghỉ từ ngày mai");
        }
    }
}
