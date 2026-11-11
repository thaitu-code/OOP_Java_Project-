package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.id.fitnessai.dto.ProfileUpdateRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.enums.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Restrained AI tool executor. Gemini chooses only identifiers from server catalogs;
 * the Java layer validates all writes, and the current authenticated user owns them. */
@Service @RequiredArgsConstructor
public class AiActionService {
    private final GeminiAiClient gemini;
    private final ObjectMapper mapper;
    private final AiAssistantService assistant;
    private final FoodRepository foods;
    private final ExerciseRepository exercises;
    private final UserProfileService profiles;
    private final Validator validator;
    private final UserRepository users;

    public record ActionResponse(String action, String message, Long recommendationId, Object profile) {}

    public ActionResponse execute(String username, String instruction) {
        if(instruction==null || instruction.isBlank() || instruction.length()>1200)
            throw new IllegalArgumentException("Yêu cầu phải có 1-1200 ký tự");
        // Do not let the model invent or override the authenticated identity.
        users.findByUsername(username).orElseThrow(()->new SecurityException("Không tìm thấy tài khoản"));
        var profile=profiles.getUserProfile(username);
        List<Food> foodCatalog=foods.findAll().stream().filter(Food::isAvailableForLogging).limit(120).toList();
        List<Exercise> exerciseCatalog=exercises.findAll().stream().filter(Exercise::isActive).limit(120).toList();
        String foodNames=foodCatalog.stream().map(f->f.getFoodId()+":"+f.getFoodName()+" ("+f.getCaloriesPer100g()+" kcal/100g)").collect(Collectors.joining("; "));
        String exerciseNames=exerciseCatalog.stream().map(e->e.getExerciseId()+":"+e.getExerciseName()).collect(Collectors.joining("; "));
        String prompt="Hôm nay tại Việt Nam là "+LocalDate.now(NutritionLog.VN_ZONE)+". " +
            "Bạn là công cụ phân tích yêu cầu của người dùng, chỉ xuất JSON thuần, không markdown. " +
            "Chọn action MEAL_PLAN (tạo/sửa thực đơn dự kiến), WORKOUT_PLAN (tạo/sửa lịch tập dự kiến), PROFILE_UPDATE (thay đổi thông tin hồ sơ được người dùng nói rõ), hoặc NONE (yêu cầu không phù hợp). " +
            "Không biến lời hỏi chung thành lệnh cập nhật. Nếu chưa đủ thông tin thì chọn NONE và điền message là câu hỏi làm rõ. " +
            "Các bản ghi kế hoạch là tương lai, tuyệt đối không đánh dấu đã ăn/đã tập. " +
            "Format: {\"action\":\"MEAL_PLAN|WORKOUT_PLAN|PROFILE_UPDATE|NONE\",\"title\":\"...\",\"message\":\"...\",\"targetCalories\":2000," +
            "\"meals\":[{\"foodId\":1,\"date\":\"YYYY-MM-DD\",\"mealType\":\"BREAKFAST|LUNCH|AFTERNOON|DINNER\",\"grams\":150}]," +
            "\"exercises\":[{\"exerciseId\":1,\"dayOfWeek\":1,\"sets\":3,\"reps\":12}]," +
            "\"profileChanges\":{\"displayName\":null,\"birthDate\":null,\"gender\":null,\"heightCm\":null,\"weightKg\":null,\"activityLevel\":null,\"goal\":null}}. " +
            "Nếu MEAL_PLAN, tạo 3-4 bữa/ngày theo các ngày được yêu cầu (mặc định 7 ngày, tối đa 7), 1-2 món mỗi bữa, tổng 1-60 món. Chỉ dùng ID món được cấp. " +
            "Nếu WORKOUT_PLAN, tạo 2-5 bài tập mỗi ngày tập, 1-6 hiệp, 1-40 lần, số ngày tập mặc định 4, tối đa 7. Chỉ dùng ID bài tập được cấp. " +
            "Nếu PROFILE_UPDATE, chỉ điền các trường mà người dùng YÊU CẦU thay đổi rõ ràng, không đoán ngày sinh/giới tính. " +
            "Mục tiêu hợp lệ: "+Arrays.toString(FitnessGoal.values())+"; vận động: "+Arrays.toString(ActivityLevel.values())+"; giới tính: "+Arrays.toString(Gender.values())+". " +
            "Hồ sơ hiện tại: "+mapper.writeValueAsString(profile)+". Danh mục món: "+foodNames+". Danh mục bài: "+exerciseNames+". " +
            "Yêu cầu: "+instruction;
        String raw=gemini.generate("Trả về duy nhất một đối tượng JSON theo schema. Không bịa id. Không chỉ dẫn nguy hiểm.",List.of(Map.of("role","user","text",prompt)));
        try {
            String json=raw.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$","");
            JsonNode root=mapper.readTree(json);
            String action=root.path("action").asText("NONE");
            String title=root.path("title").asText("Kế hoạch AI");
            if(title.isBlank()) title="Kế hoạch AI";
            title=title.substring(0,Math.min(180,title.length()));
            String message=root.path("message").asText("Được tạo theo yêu cầu của bạn.");
            if(message.isBlank()) message="Được tạo theo yêu cầu của bạn.";
            message=message.substring(0,Math.min(3000,message.length()));
            if("NONE".equals(action)) return new ActionResponse("NONE",message,null,null);
            if("MEAL_PLAN".equals(action)) {
                if(foodCatalog.isEmpty()) throw new IllegalArgumentException("Thư viện thực phẩm trống");
                Set<Integer> allowed=foodCatalog.stream().map(Food::getFoodId).collect(Collectors.toSet());
                List<AiAssistantService.MealItem> items=new ArrayList<>();
                for(JsonNode n:root.path("meals")) {
                    int id=n.path("foodId").asInt(-1);
                    if(!allowed.contains(id)) throw new IllegalArgumentException("AI chọn thực phẩm không có trong thư viện");
                    LocalDate date=LocalDate.parse(n.path("date").asText());
                    LocalDate today=LocalDate.now(NutritionLog.VN_ZONE);
                    if(date.isBefore(today)||date.isAfter(today.plusDays(6)))throw new IllegalArgumentException("Lịch ăn AI chỉ hỗ trợ 7 ngày kể từ hôm nay");
                    items.add(new AiAssistantService.MealItem(id,date,MealType.valueOf(n.path("mealType").asText()),new BigDecimal(n.path("grams").asText())));
                }
                BigDecimal kcal=new BigDecimal(root.path("targetCalories").asText("0"));
                if(kcal.compareTo(BigDecimal.valueOf(1000))<0 || kcal.compareTo(BigDecimal.valueOf(5000))>0)
                    throw new IllegalArgumentException("Mục tiêu năng lượng AI tạo không hợp lệ");
                var result=assistant.create(username,new AiAssistantService.PlanRequest("MEAL_PLAN",title,message,null,null,kcal,items,null,null));
                return new ActionResponse(action,"Đã lưu thực đơn dự kiến. Xem tab Lịch ăn uống; nhật ký đã ăn không bị thay đổi.",result.recommendation().id(),null);
            }
            if("WORKOUT_PLAN".equals(action)) {
                if(exerciseCatalog.isEmpty()) throw new IllegalArgumentException("Thư viện bài tập trống");
                Set<Integer> allowed=exerciseCatalog.stream().map(Exercise::getExerciseId).collect(Collectors.toSet());
                List<AiAssistantService.ExerciseItem> items=new ArrayList<>();
                for(JsonNode n:root.path("exercises")) {
                    int id=n.path("exerciseId").asInt(-1);
                    if(!allowed.contains(id)) throw new IllegalArgumentException("AI chọn bài tập không có trong thư viện");
                    items.add(new AiAssistantService.ExerciseItem(id,n.path("dayOfWeek").asInt(),n.path("sets").asInt(),n.path("reps").asInt()));
                }
                var result=assistant.create(username,new AiAssistantService.PlanRequest("WORKOUT_PLAN",title,message,null,null,null,null,items,null));
                return new ActionResponse(action,"Đã lưu lịch tập dự kiến. Xem lịch tập AI; chưa ghi nhận buổi tập đã hoàn thành.",result.recommendation().id(),null);
            }
            if("PROFILE_UPDATE".equals(action)) {
                JsonNode n=root.path("profileChanges");
                if(!n.isObject() || !n.properties().stream().anyMatch(e->!e.getValue().isNull()&&!e.getValue().asText().isBlank()))
                    throw new IllegalArgumentException("AI không tìm thấy trường hồ sơ cần sửa");
                ProfileUpdateRequest req=new ProfileUpdateRequest();
                req.setDisplayName(field(n,"displayName",profile.getDisplayName()));
                req.setBirthDate(LocalDate.parse(field(n,"birthDate",String.valueOf(profile.getBirthDate()))));
                req.setGender(Gender.valueOf(field(n,"gender",String.valueOf(profile.getGender()))));
                req.setHeightCm(new BigDecimal(field(n,"heightCm",String.valueOf(profile.getHeightCm()))));
                req.setWeightKg(new BigDecimal(field(n,"weightKg",String.valueOf(profile.getWeightKg()))));
                req.setActivityLevel(ActivityLevel.valueOf(field(n,"activityLevel",String.valueOf(profile.getActivityLevel()))));
                req.setGoal(FitnessGoal.valueOf(field(n,"goal",String.valueOf(profile.getGoal()))));
                // Explicit allow-list; apply the existing profile service so BMI/BMR/TDEE are recalculated.
                var violations=validator.validate(req);
                if(!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
                var updated=profiles.updateUserProfile(username,req);
                return new ActionResponse(action,"Đã cập nhật hồ sơ và tính lại chỉ số. "+message,null,updated);
            }
            return new ActionResponse("NONE","Chưa hỗ trợ thao tác này.",null,null);
        } catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("Không thể xác thực dữ liệu hành động AI: "+e.getMessage(),e);}
    }
    private String field(JsonNode obj,String name,String fallback){JsonNode x=obj.path(name);return x.isMissingNode()||x.isNull()||x.asText().isBlank()?fallback:x.asText();}
}
