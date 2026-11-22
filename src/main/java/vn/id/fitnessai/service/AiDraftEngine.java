package vn.id.fitnessai.service;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;
import vn.id.fitnessai.dto.*;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Builds trusted previews and executes only their persisted, validated payloads. No provider calls. */
@Service @RequiredArgsConstructor
public class AiDraftEngine {
    private final AiAccess access;
    private final UserProfileRepository profiles;
    private final UserProfileService profileService;
    private final AiAssistantService plans;
    private final AiPlanApplyService apply;
    private final AiScheduleService schedule;
    private final NutritionService nutrition;
    private final FoodRepository foods;
    private final ExerciseRepository exercises;
    private final AiRecommendationRepository recommendations;
    private final AiMealPlanItemRepository mealItems;
    private final ObjectMapper mapper;
    private final Validator validator;

    public record Prepared(String kind, String payload, String preview, String fingerprint) {}
    public record Execution(String message, Long recommendationId) {}
    private static final Set<String> KINDS = Set.of("PROFILE_UPDATE", "MEAL_LOG", "MEAL_UPDATE", "MEAL_DELETE", "MEAL_PLAN", "WORKOUT_PLAN");
    private static final Set<String> PROFILE_FIELDS = Set.of("displayName", "birthDate", "gender", "heightCm", "weightKg", "activityLevel", "goal", "targetWeightKg", "trainingPreferences");

    @Transactional(readOnly = true)
    public String baseFingerprint(String username) {
        var user = access.require(username);
        var p = profiles.findByUserId(user.getId()).orElse(null);
        var log = nutrition.getDailyLog(username, LocalDate.now(NutritionLog.VN_ZONE));
        List<Object> values = new ArrayList<>();
        values.add(LocalDate.now(NutritionLog.VN_ZONE)); values.add(p == null ? "no-profile" : p.getVersion());
        values.add(plans.recommendations(username));
        values.add(log.getMeals().stream().flatMap(m -> m.getItems().stream()).map(i -> List.of(i.getDetailId(), i.getVersion())).toList());
        return hash(mapper.writeValueAsString(values));
    }

    @Transactional(readOnly = true)
    public Prepared prepare(String username, JsonNode node) {
        String kind = node.path("kind").asText();
        if (!KINDS.contains(kind) || !node.path("payload").isObject()) throw new IllegalArgumentException("AI đề xuất thao tác không hỗ trợ");
        var payload = ((tools.jackson.databind.node.ObjectNode) node.path("payload")).deepCopy();
        payload.put("businessDate", LocalDate.now(NutritionLog.VN_ZONE).toString());
        String preview = preview(username, kind, payload);
        String json = mapper.writeValueAsString(payload);
        return new Prepared(kind, json, preview, fingerprint(username, kind, payload));
    }

    @Transactional(readOnly = true)
    public String fingerprint(String username, String kind, JsonNode payload) {
        var user = access.require(username);
        var profile = profiles.findByUserId(user.getId()).orElse(null);
        List<Object> data = new ArrayList<>(); data.add(LocalDate.now(NutritionLog.VN_ZONE)); data.add(kind);
        // Independent drafts remain valid after unrelated confirmations.
        if (kind.equals("PROFILE_UPDATE") || kind.endsWith("PLAN")) data.add(profile == null ? "no-profile" : profile.getVersion());
        if (kind.equals("MEAL_UPDATE") || kind.equals("MEAL_DELETE")) {
            var meal = existingMeal(username, payload); data.add(List.of(meal.getDetailId(), meal.getVersion()));
        }
        if (payload.path("parentId").isNumber()) {
            var parent = recommendations.findById(payload.path("parentId").asLong()).orElseThrow();
            data.add(List.of(parent.getId(), parent.getVersion(), parent.getStatus()));
        }
        if (kind.equals("WORKOUT_PLAN")) data.add(recommendations.findByProfile_User_IdAndTypeOrderByIdDesc(user.getId(), "WORKOUT_PLAN")
                .stream().filter(r -> Set.of("APPLIED", "SCHEDULED").contains(r.getStatus()))
                .map(r -> Arrays.asList(r.getId(), r.getVersion(), r.getStatus(), r.getEffectiveUntil())).toList());
        if (kind.startsWith("MEAL")) {
            for (JsonNode item : items(payload)) {
                int id = item.path("foodId").asInt(-1);
                var food = foods.findById(id).orElseThrow(() -> new IllegalArgumentException("Thực phẩm không tồn tại"));
                data.add(List.of(id, food.isAvailableForLogging(), food.getCaloriesPer100g(), food.getProteinPer100g(), food.getCarbsPer100g(), food.getFatPer100g()));
            }
        }
        if (kind.equals("WORKOUT_PLAN")) for (JsonNode item : payload.path("exercises")) {
            var e = exercises.findById(item.path("exerciseId").asInt(-1)).orElseThrow(() -> new IllegalArgumentException("Bài tập không tồn tại"));
            data.add(List.of(e.getExerciseId(), e.isActive(), e.getLevels().stream().map(l -> Arrays.asList(l.getId(), l.isActive(), l.getSets(), l.getReps(), l.getSecondsPerSet(), l.getRestSeconds(), l.getEstimatedSeconds())).toList()));
        }
        return hash(mapper.writeValueAsString(data));
    }

    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private List<JsonNode> items(JsonNode payload) {
        var result = new ArrayList<JsonNode>();
        for (JsonNode n : payload.path("items")) result.add(n);
        for (JsonNode n : payload.path("meals")) result.add(n);
        if (payload.has("foodId")) result.add(payload);
        return result;
    }

    private String preview(String username, String kind, JsonNode p) {
        if (kind.equals("PROFILE_UPDATE")) return profilePreview(username, p);
        if (kind.equals("MEAL_PLAN") || kind.equals("WORKOUT_PLAN")) {
            var request = planRequest(username, kind, p);
            var result = plans.preview(username, request);
            StringBuilder out = new StringBuilder(request.title()).append("\n").append(request.content());
            if (request.parentId() != null) out.append("\nTạo phiên bản thay thế kế hoạch #").append(request.parentId());
            if (kind.equals("WORKOUT_PLAN")) {
                out.append("\nHiệu lực: ").append(request.workoutStartDate()).append(request.overrideDate() == null ? "; lặp tuần" : "; chỉ ngày này");
                for (var item : request.exercises()) {
                    var e = exercises.findById(item.exerciseId()).orElseThrow();
                    out.append("\n").append(weekday(item.dayOfWeek())).append(": ").append(e.getExerciseName());
                    if (item.levelId() != null) {
                        var l = e.getLevels().stream().filter(x -> x.getId().equals(item.levelId())).findFirst().orElseThrow();
                        out.append(" — ").append(l.getName()).append(": ").append(l.getSets()).append(" hiệp × ")
                                .append(l.getReps() == null ? l.getSecondsPerSet() + " giây" : l.getReps() + " lần");
                    } else out.append(" — ").append(item.sets()).append(" hiệp × ").append(item.reps()).append(" lần");
                }
                out.append("\nNgày nghỉ: ").append(request.restDays().stream().map(this::weekday).toList());
                out.append("\nXác nhận chỉ kích hoạt lịch; không bắt đầu hoặc công nhận bài tập.");
            } else {
                out.append("\nMục tiêu mỗi ngày: ").append(request.targetCalories()).append(" kcal; đạm ").append(request.targetProtein())
                        .append(" g, tinh bột ").append(request.targetCarbs()).append(" g, béo ").append(request.targetFat()).append(" g.");
                Map<LocalDate, BigDecimal> totals = new TreeMap<>();
                for (var item : request.meals()) {
                    var f = foods.findById(item.foodId()).orElseThrow();
                    BigDecimal kcal = f.getCaloriesPer100g().multiply(item.grams()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    totals.merge(item.date(), kcal, BigDecimal::add);
                    out.append("\n").append(item.date()).append(" · ").append(mealName(item.mealType())).append(": ").append(f.getFoodName()).append(" ").append(item.grams()).append(" g — ").append(kcal).append(" kcal");
                }
                out.append("\nTổng từng ngày: ").append(totals).append("\nXác nhận lưu thực đơn dự kiến, chưa ghi đã ăn.");
            }
            return out.toString();
        }
        if (kind.equals("MEAL_DELETE")) {
            var detail = existingMeal(username, p);
            return "Xóa món hôm nay: " + detail.getFoodNameSnapshot() + " · " + mealName(detail.getMealType()) + " · " + detail.getWeightGrams() + " g";
        }
        StringBuilder text = new StringBuilder(kind.equals("MEAL_UPDATE") ? "Sửa món đã ghi hôm nay:" : "Ghi những món đã ăn hôm nay:");
        List<JsonNode> entries = kind.equals("MEAL_UPDATE") ? List.of(p) : items(p);
        if (entries.isEmpty() || entries.size() > 60) throw new IllegalArgumentException("Cần 1-60 món ăn");
        if (kind.equals("MEAL_UPDATE")) {
            var old = existingMeal(username, p);
            text.append("\nTrước: ").append(old.getFoodNameSnapshot()).append(" · ").append(mealName(old.getMealType())).append(" · ").append(old.getWeightGrams()).append(" g");
        }
        for (JsonNode entry : entries) {
            var request = mealRequest(entry); validate(request);
            var f = foods.findById(request.getFoodId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thực phẩm"));
            if (!f.isAvailableForLogging()) throw new IllegalArgumentException("Thực phẩm đã ngừng hoạt động");
            text.append("\n").append(f.getFoodName()).append(" · ").append(mealName(request.getMealType())).append(" · ").append(request.getWeightGrams()).append(" g");
        }
        return text.toString();
    }

    private String profilePreview(String username, JsonNode payload) {
        var user = access.require(username);
        var p = profiles.findByUserId(user.getId()).orElseThrow(() -> new IllegalArgumentException("Hãy hoàn tất hồ sơ trước khi cập nhật qua chat"));
        JsonNode changes = payload.path("changes");
        if (!changes.isObject() || changes.isEmpty()) throw new IllegalArgumentException("Không có thông tin hồ sơ cần sửa");
        Map<String,Object> before = profileValues(p);
        StringBuilder out = new StringBuilder("Cập nhật hồ sơ:");
        for (var field : changes.properties()) {
            if (!PROFILE_FIELDS.contains(field.getKey()) || field.getValue().isNull() || field.getValue().asText().isBlank())
                throw new IllegalArgumentException("Trường hồ sơ không được phép hoặc thiếu giá trị");
            if (Set.of("weightKg", "heightCm", "targetWeightKg").contains(field.getKey()) && new BigDecimal(field.getValue().asText()).stripTrailingZeros().scale() > 2)
                throw new IllegalArgumentException("Số đo chỉ hỗ trợ tối đa hai chữ số thập phân");
            Object oldValue = before.get(field.getKey());
            String oldText = oldValue == null || oldValue.toString().isBlank() ? "Chưa thiết lập" : oldValue.toString();
            out.append("\n").append(label(field.getKey())).append(": ").append(oldText).append(" → ").append(field.getValue().asText());
        }
        validate(profileRequest(p, changes));
        int age = java.time.Period.between(profileRequest(p, changes).getBirthDate(), LocalDate.now(NutritionLog.VN_ZONE)).getYears();
        if (age < 15 || age > 65) throw new IllegalArgumentException("Hệ thống chỉ hỗ trợ độ tuổi từ 15 đến 65");
        if (changes.has("targetWeightKg")) {
            BigDecimal n = decimal(changes, "targetWeightKg");
            if (n.compareTo(new BigDecimal("20")) < 0 || n.compareTo(new BigDecimal("400")) > 0) throw new IllegalArgumentException("Cân nặng đích phải từ 20 đến 400 kg");
        }
        if (changes.path("trainingPreferences").asText().length() > 2000) throw new IllegalArgumentException("Thông tin tập luyện tối đa 2000 ký tự");
        return out.toString();
    }

    private String label(String field) { return switch (field) {
        case "weightKg" -> "Cân nặng hiện tại (kg)"; case "targetWeightKg" -> "Cân nặng đích (kg)";
        case "heightCm" -> "Chiều cao (cm)"; case "trainingPreferences" -> "Điều kiện và hạn chế tập luyện";
        case "goal" -> "Mục tiêu"; case "activityLevel" -> "Mức vận động";
        case "displayName" -> "Tên hiển thị"; case "birthDate" -> "Ngày sinh"; case "gender" -> "Giới tính"; default -> field;
    }; }
    private String weekday(int day) { return day == 7 ? "Chủ nhật" : "Thứ " + (day + 1); }
    private String mealName(MealType meal) { return switch(meal) { case BREAKFAST -> "Bữa sáng"; case LUNCH -> "Bữa trưa"; case AFTERNOON -> "Bữa phụ chiều"; case DINNER -> "Bữa tối"; }; }

    private Map<String,Object> profileValues(UserProfile p) {
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("displayName", p.getDisplayName()); data.put("birthDate", p.getBirthDate()); data.put("gender", p.getGender());
        data.put("heightCm", p.getHeightCm()); data.put("weightKg", p.getWeightKg()); data.put("goal", p.getGoal()); data.put("activityLevel", p.getActivityLevel());
        data.put("targetWeightKg", p.getTargetWeightKg()); data.put("trainingPreferences", p.getTrainingPreferences()); return data;
    }

    private ProfileUpdateRequest profileRequest(UserProfile p, JsonNode changes) {
        var fields = profileValues(p); changes.properties().forEach(e -> fields.put(e.getKey(), e.getValue().asText()));
        var req = new ProfileUpdateRequest(); req.setExpectedVersion(p.getVersion()); req.setDisplayName(String.valueOf(fields.get("displayName")));
        req.setBirthDate(LocalDate.parse(String.valueOf(fields.get("birthDate")))); req.setGender(Gender.valueOf(String.valueOf(fields.get("gender"))));
        req.setHeightCm(new BigDecimal(String.valueOf(fields.get("heightCm")))); req.setWeightKg(new BigDecimal(String.valueOf(fields.get("weightKg"))));
        req.setActivityLevel(ActivityLevel.valueOf(String.valueOf(fields.get("activityLevel")))); req.setGoal(FitnessGoal.valueOf(String.valueOf(fields.get("goal")))); return req;
    }

    private LogDetailResponse existingMeal(String username, JsonNode p) {
        var result = nutrition.getDailyLog(username, LocalDate.now(NutritionLog.VN_ZONE)).getMeals().stream().flatMap(m -> m.getItems().stream())
                .filter(i -> i.getDetailId() == p.path("detailId").asInt(-1)).findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món trong nhật ký hôm nay"));
        if (!Objects.equals(result.getVersion(), p.path("expectedVersion").asLong(-1))) throw new IllegalStateException("Món đã thay đổi; hãy tải lại");
        return result;
    }
    private LogDetailRequest mealRequest(JsonNode n) {
        LogDetailRequest req = new LogDetailRequest(); req.setFoodId(n.path("foodId").asInt(-1));
        req.setMealType(MealType.valueOf(n.path("mealType").asText())); req.setWeightGrams(decimal(n, "grams"));
        req.setNote("Ghi qua trợ lý AI, người dùng xác nhận"); return req;
    }
    private BigDecimal decimal(JsonNode n, String key) { return new BigDecimal(n.path(key).asText()); }
    private <T> void validate(T value) {
        var errors = validator.validate(value); if (!errors.isEmpty()) throw new IllegalArgumentException(errors.iterator().next().getMessage());
    }

    private AiAssistantService.PlanRequest planRequest(String username, String kind, JsonNode p) {
        LocalDate today = LocalDate.now(NutritionLog.VN_ZONE);
        Long parentId = p.path("parentId").isNumber() ? p.path("parentId").asLong() : null;
        if (parentId != null) {
            var parent = recommendations.findById(parentId).orElseThrow(() -> new IllegalArgumentException("Kế hoạch gốc không tồn tại"));
            if (!parent.getProfile().getUser().getId().equals(access.require(username).getId()) || !kind.equals(parent.getType())) throw new SecurityException("Kế hoạch không thuộc bạn");
            if (parent.getVersion() != p.path("expectedVersion").asLong(-1)) throw new IllegalStateException("Kế hoạch đã thay đổi; hãy tạo lại bản xem trước");
        }
        List<AiAssistantService.MealItem> meals = new ArrayList<>();
        for (JsonNode i : p.path("meals")) {
            LocalDate date = LocalDate.parse(i.path("date").asText());
            if (date.isBefore(today) || date.isAfter(today.plusDays(90))) throw new IllegalArgumentException("Ngày thực đơn phải từ hôm nay đến 90 ngày tới");
            meals.add(new AiAssistantService.MealItem(i.path("foodId").asInt(), date, MealType.valueOf(i.path("mealType").asText()), decimal(i,"grams")));
        }
        List<AiAssistantService.ExerciseItem> exercises = new ArrayList<>();
        for (JsonNode i : p.path("exercises")) exercises.add(new AiAssistantService.ExerciseItem(i.path("exerciseId").asInt(), i.path("dayOfWeek").asInt(),
                i.path("sets").isNumber() ? i.path("sets").asInt() : null, i.path("reps").isNumber() ? i.path("reps").asInt() : null,
                i.path("levelId").isNumber() ? i.path("levelId").asInt() : null));
        List<Integer> rest = new ArrayList<>(); for (JsonNode i : p.path("restDays")) rest.add(i.asInt(-1));
        LocalDate start = kind.equals("WORKOUT_PLAN") ? LocalDate.parse(p.path("startDate").asText()) : null;
        LocalDate override = p.path("specificDate").isTextual() ? LocalDate.parse(p.path("specificDate").asText()) : null;
        if (kind.equals("WORKOUT_PLAN") && (rest.contains(start.getDayOfWeek().getValue()) || override != null && rest.contains(override.getDayOfWeek().getValue())))
            schedule.requireCanRest(access.require(username).getId(), start, today);
        BigDecimal target = kind.equals("MEAL_PLAN") ? decimal(p,"targetCalories") : null;
        if (target != null && (target.signum() <= 0 || target.compareTo(new BigDecimal("10000")) > 0)) throw new IllegalArgumentException("Mục tiêu năng lượng không hợp lệ");
        return new AiAssistantService.PlanRequest(kind, p.path("title").asText(), p.path("content").asText(), null, null, target,
                meals, exercises, start, rest, override, parentId, kind.equals("MEAL_PLAN") ? decimal(p,"targetProtein") : null,
                kind.equals("MEAL_PLAN") ? decimal(p,"targetCarbs") : null, kind.equals("MEAL_PLAN") ? decimal(p,"targetFat") : null);
    }

    /** Caller holds the user's row lock and commits draft status and domain writes together. */
    public Execution execute(String username, AiActionDraft draft) {
        JsonNode p = mapper.readTree(draft.getPayload());
        preview(username, draft.getKind(), p);
        switch (draft.getKind()) {
            case "PROFILE_UPDATE" -> {
                var user = access.require(username); var profile = profiles.findByUserId(user.getId()).orElseThrow(); JsonNode changes = p.path("changes");
                profileService.updateUserProfile(username, profileRequest(profile, changes));
                if (changes.has("targetWeightKg")) profile.setTargetWeightKg(decimal(changes,"targetWeightKg"));
                if (changes.has("trainingPreferences")) profile.setTrainingPreferences(changes.path("trainingPreferences").asText());
                profiles.saveAndFlush(profile);
                return new Execution("Đã cập nhật đúng thông tin đã xác nhận. Lịch hiện tại được giữ; có thể yêu cầu AI đề xuất lịch phù hợp hồ sơ mới.", null);
            }
            case "MEAL_LOG" -> { for (JsonNode n : items(p)) nutrition.addFoodByAssistant(username, mealRequest(n), LocalDate.parse(p.path("businessDate").asText())); return new Execution("Đã ghi các món đã xác nhận vào nhật ký hôm nay.", null); }
            case "MEAL_UPDATE" -> {
                var base = mealRequest(p); var req = new LogDetailUpdateRequest(); req.setFoodId(base.getFoodId()); req.setMealType(base.getMealType());
                req.setWeightGrams(base.getWeightGrams()); req.setNote(base.getNote()); req.setExpectedVersion(p.path("expectedVersion").asLong());
                nutrition.updateDetailByAssistant(username, p.path("detailId").asInt(), req).orElseThrow(); return new Execution("Đã sửa món trong nhật ký hôm nay.", null);
            }
            case "MEAL_DELETE" -> { nutrition.deleteDetail(username, p.path("detailId").asInt(), p.path("expectedVersion").asLong()).orElseThrow(); return new Execution("Đã xóa món đã xác nhận khỏi nhật ký hôm nay.", null); }
            case "MEAL_PLAN", "WORKOUT_PLAN" -> {
                var request = planRequest(username, draft.getKind(), p); var result = plans.create(username, request); Long id = result.recommendation().id();
                var saved = recommendations.findById(id).orElseThrow(); saved.setSourceDraftId(draft.getId()); saved.setSourceTurnId(draft.getTurnId());
                if (draft.getKind().equals("WORKOUT_PLAN")) apply.applyWorkout(username, id);
                else if (request.parentId() != null) {
                    var parent = recommendations.findById(request.parentId()).orElseThrow(); parent.setStatus("REPLACED");
                    var old = mealItems.findByRecommendation_IdOrderByPlanDateAscIdAsc(parent.getId());
                    Set<Long> consumed = new HashSet<>();
                    for (var item : mealItems.findByRecommendation_IdOrderByPlanDateAscIdAsc(id)) old.stream().filter(o -> o.getPlanDate().equals(item.getPlanDate()) && o.getMealType() == item.getMealType()
                            && o.getFood().getFoodId().equals(item.getFood().getFoodId()) && !consumed.contains(o.getId())).findFirst().ifPresent(o -> {
                                consumed.add(o.getId()); item.setSourceItemId(o.getSourceItemId() == null ? o.getId() : o.getSourceItemId());
                            });
                }
                return new Execution("Đã lưu kế hoạch #" + id + " đúng bản đã xác nhận. Chưa ghi đã ăn hoặc đã tập.", id);
            }
            default -> throw new IllegalArgumentException("Thao tác không được phép");
        }
    }
}
