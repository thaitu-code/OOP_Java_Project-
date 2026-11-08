package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.id.fitnessai.entity.Exercise;
import vn.id.fitnessai.repository.ExerciseRepository;
import java.util.*;
import java.util.stream.Collectors;

/** Only existing, active exercise IDs are allowed in AI plans. */
@Service @RequiredArgsConstructor
public class AiWeeklyWorkoutService {
    private final GeminiAiClient gemini;
    private final AiAssistantService assistant;
    private final ExerciseRepository exercises;
    private final ObjectMapper mapper;

    public record WeeklyRequest(String goal, String level, Integer daysPerWeek) {}
    public AiAssistantService.PlanResult generate(String username, WeeklyRequest req) {
        if (req == null) throw new IllegalArgumentException("Thiếu lựa chọn lịch tập");
        int days = req.daysPerWeek() == null ? 4 : req.daysPerWeek();
        if (days < 1 || days > 7) throw new IllegalArgumentException("Số ngày tập phải từ 1 đến 7");
        String goal = normalize(req.goal(), Set.of("GENERAL", "MUSCLE", "WEIGHT_LOSS", "ENDURANCE"), "GENERAL");
        String level = normalize(req.level(), Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED"), "BEGINNER");
        List<Exercise> catalog = exercises.findAll().stream().filter(Exercise::isActive).limit(150).toList();
        if (catalog.isEmpty()) throw new IllegalStateException("Thư viện chưa có bài tập hoạt động. Vui lòng thêm bài tập trước.");
        Map<Integer,Exercise> byId = catalog.stream().collect(Collectors.toMap(Exercise::getExerciseId,e->e));
        StringBuilder candidates = new StringBuilder();
        for (Exercise e : catalog) candidates.append(e.getExerciseId()).append(": ").append(e.getExerciseName()).append("; ");
        String instructions = "Bạn là huấn luyện viên FitnessAI. Tạo lịch 7 ngày (thứ 2=1 đến chủ nhật=7) bằng bài tập CÓ SẴN. " +
                "Mục tiêu=" + goal + "; trình độ=" + level + "; số ngày tập=" + days + ". " +
                "Chỉ trả JSON hợp lệ, không markdown: {\"title\":\"...\",\"note\":\"...\",\"exercises\":[{\"exerciseId\":1,\"dayOfWeek\":1,\"sets\":3,\"reps\":10}]}. " +
                "Tạo bài ở chính xác " + days + " ngày khác nhau, chia ngày nghỉ hợp lý. Mỗi ngày tập 2-5 bài; mỗi bài 1-6 hiệp, 1-40 reps. " +
                "Không bịa ID. Danh mục được phép: " + candidates;
        String raw = gemini.generate("Bạn xuất JSON thuần để hệ thống xử lý. Không cung cấp lời khuyên nguy hiểm; ưu tiên nghỉ phục hồi.",
                List.of(Map.of("role","user","text",instructions)));
        try {
            String json = raw.trim();
            if (json.startsWith("```")) json=json.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
            JsonNode root=mapper.readTree(json);
            JsonNode items=root.path("exercises");
            if (!items.isArray() || items.size() == 0 || items.size()>80) throw new IllegalArgumentException("AI trả số bài không hợp lệ");
            List<AiAssistantService.ExerciseItem> plan=new ArrayList<>();
            Set<Integer> activeDays=new HashSet<>();
            for(JsonNode n:items){
                int id=n.path("exerciseId").asInt(-1), day=n.path("dayOfWeek").asInt(-1), sets=n.path("sets").asInt(-1), reps=n.path("reps").asInt(-1);
                if(!byId.containsKey(id)||day<1||day>7||sets<1||sets>6||reps<1||reps>40)
                    throw new IllegalArgumentException("AI trả bài tập không thuộc thư viện hoặc thông số không hợp lệ");
                activeDays.add(day); plan.add(new AiAssistantService.ExerciseItem(id,day,sets,reps));
            }
            if(activeDays.size()!=days) throw new IllegalArgumentException("AI trả số ngày tập khác lựa chọn");
            String title=root.path("title").asText("Lịch tập AI 7 ngày");
            String note=root.path("note").asText("Kế hoạch gợi ý, chỉ áp dụng khi người dùng lựa chọn.");
            if(title.isBlank())title="Lịch tập AI 7 ngày";
            if(note.isBlank())note="Lịch tập được tạo dựa trên thư viện bài tập hiện có.";
            title=title.substring(0,Math.min(180,title.length()));
            note=note.substring(0,Math.min(3000,note.length()));
            return assistant.create(username,new AiAssistantService.PlanRequest("WORKOUT_PLAN",title,note,null,null,null,null,plan));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("AI trả dữ liệu lịch tập không đúng định dạng. Hãy tạo lại.",e); }
    }
    private String normalize(String value,Set<String> allowed,String fallback){return value!=null&&allowed.contains(value)?value:fallback;}
}
