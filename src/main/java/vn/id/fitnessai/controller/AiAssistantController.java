package vn.id.fitnessai.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.service.AiAssistantService;
import java.security.Principal;
import java.util.*;
@Controller
@RequiredArgsConstructor

public class AiAssistantController {

    private final AiAssistantService service;
    @GetMapping("/ai") public String page(){
       return "ai";
    }

    @RestController
    @RequestMapping("/api/ai")
    @RequiredArgsConstructor
    public static class Api {
        private final AiAssistantService service;
        private final vn.id.fitnessai.service.AiConversationService conversation;
        private final vn.id.fitnessai.service.AiConversationStore conversationStore;
        private final vn.id.fitnessai.service.AiDraftService drafts;
        private final vn.id.fitnessai.service.AiNotificationService notifications;
        private final vn.id.fitnessai.service.AiScheduleService schedule;
        private final vn.id.fitnessai.service.AiAccess access;
        private final vn.id.fitnessai.service.AiPlanApplyService apply;

        @PostMapping("/recommendations/{id}/log-today")
        public Map<String,Object> logToday(Principal p,@PathVariable Long id){
            return apply.logToday(p.getName(),id);
        }
        public record MealSelectionRequest(List<vn.id.fitnessai.service.AiPlanApplyService.MealSelection> items) {}
        @PostMapping("/recommendations/{id}/log-meals")
        public Map<String,Object> logMeals(Principal p, @PathVariable Long id, @RequestBody MealSelectionRequest request) {
            if (request.items() == null) throw new IllegalArgumentException("Hãy chọn món đã ăn");
            return apply.logSelected(p.getName(), id, request.items());
        }

        @PostMapping("/recommendations/{id}/apply-workout")
        public Map<String,Object> applyWorkout(Principal p,@PathVariable Long id){
            return apply.applyWorkout(p.getName(),id);
        }
        @PostMapping("/recommendations/{id}/start-today-workout")
        public Map<String,Object> startWorkout(Principal p,@PathVariable Long id){
            return apply.startTodayWorkout(p.getName(),id);
        }
        public record ActionRequest(String instruction) {}

        @PostMapping("/actions")
        public ResponseEntity<Map<String,String>> action(Principal p,@RequestBody ActionRequest r){
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "Hãy gửi yêu cầu qua chat thống nhất; thao tác này không còn ghi trực tiếp."));
        }
        @PostMapping("/weekly-workout")
        public ResponseEntity<Map<String,String>> weekly(Principal p){
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "Tạo lịch qua hội thoại AI để có đủ ngữ cảnh và bản xem trước."));
        }
        public record ChatRequest(String sessionId,String content,UUID requestId) {}
        public record ChatSession(String sessionId, vn.id.fitnessai.service.AiConversationStore.Reply activeTurn) {}

        private String currentSession(jakarta.servlet.http.HttpSession session) {
            synchronized (session) {
                if (session.getAttribute("fitness-ai-context") == null) session.setAttribute("fitness-ai-context", UUID.randomUUID().toString().replace("-", ""));
                return session.getAttribute("fitness-ai-context").toString();
            }
        }
        private void requireCurrentSession(jakarta.servlet.http.HttpSession session, String supplied) {
            if (!currentSession(session).equals(supplied)) throw new SecurityException("Phiên chat đã thay đổi. Hãy tải lại trang trước khi tiếp tục.");
        }

        @GetMapping("/chat/session")
        public ChatSession session(Principal p, jakarta.servlet.http.HttpSession session) {
            String id = currentSession(session);
            return new ChatSession(id, conversationStore.activeTurn(p.getName(), id));
        }

        @GetMapping("/chat")
        public AiAssistantService.ChatPage history(Principal p, jakarta.servlet.http.HttpSession session, @RequestParam(required=false) Long before){
            return service.history(p.getName(), currentSession(session), before);
        }
        @PostMapping("/chat")
        public vn.id.fitnessai.service.AiConversationStore.Reply send(Principal p, jakarta.servlet.http.HttpSession session, @RequestBody ChatRequest req){
            requireCurrentSession(session, req.sessionId());
            return conversation.send(p.getName(),req.sessionId(),req.content(),req.requestId());
        }

        @GetMapping("/chat/turns/{requestId}")
        public vn.id.fitnessai.service.AiConversationStore.Reply turn(Principal p, jakarta.servlet.http.HttpSession session, @PathVariable UUID requestId) {
            var result = conversationStore.reply(p.getName(), requestId);
            if (result.userMessage() == null) throw new SecurityException("Lượt chat không còn khả dụng");
            requireCurrentSession(session, result.userMessage().sessionId());
            return result;
        }
        @GetMapping("/proposals")
        public List<vn.id.fitnessai.service.AiConversationStore.Proposal> pending(Principal p, jakarta.servlet.http.HttpSession session, @RequestParam String sessionId) {
            requireCurrentSession(session, sessionId);
            return conversationStore.pending(p.getName(), sessionId);
        }
        @PostMapping("/proposals/{id}/confirm")
        public vn.id.fitnessai.service.AiDraftService.Result confirm(Principal p, @PathVariable UUID id) { return drafts.decide(p.getName(), id, true); }
        @PostMapping("/proposals/{id}/cancel")
        public vn.id.fitnessai.service.AiDraftService.Result cancel(Principal p, @PathVariable UUID id) { return drafts.decide(p.getName(), id, false); }
        @GetMapping("/notifications")
        public List<vn.id.fitnessai.service.AiNotificationService.View> notifications(Principal p) { return notifications.refresh(p.getName()); }
        @PatchMapping("/notifications/{id}/read")
        public vn.id.fitnessai.service.AiNotificationService.View readNotification(Principal p, @PathVariable Long id) { return notifications.read(p.getName(), id); }
        @GetMapping("/schedule")
        public Map<String,Object> effective(Principal p, @RequestParam(required=false) java.time.LocalDate date) {
            var day = date == null ? java.time.LocalDate.now(vn.id.fitnessai.entity.NutritionLog.VN_ZONE) : date;
            return schedule.effective(access.require(p.getName()).getId(), day).map(r -> service.detail(p.getName(), r.getId())).orElse(Map.of());
        }

        @DeleteMapping("/chat/{id}")

        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void delete(Principal p,@PathVariable Long id){
            service.delete(p.getName(),id);
        }
        @GetMapping("/recommendations")
        public List<AiAssistantService.RecommendationView> recs(Principal p){
            return service.recommendations(p.getName());
        }

        @PostMapping("/recommendations")
        public AiAssistantService.PlanResult create(Principal p,@RequestBody AiAssistantService.PlanRequest request){
            return service.create(p.getName(),request);
        }
        @GetMapping("/recommendations/{id}")
        public Map<String,Object> detail(Principal p,@PathVariable Long id){
            return service.detailForDisplay(p.getName(),id);
        }
        @PatchMapping("/recommendations/{id}/read")
        public AiAssistantService.RecommendationView read(Principal p,@PathVariable Long id){
            return service.read(p.getName(),id);
        }
        @ExceptionHandler({IllegalArgumentException.class,SecurityException.class,NoSuchElementException.class,IllegalStateException.class})
        public ResponseEntity<Map<String,String>> error(Exception e){
            HttpStatus status=e instanceof vn.id.fitnessai.service.AiRateLimitException?HttpStatus.TOO_MANY_REQUESTS:e instanceof SecurityException?HttpStatus.FORBIDDEN:e instanceof NoSuchElementException?HttpStatus.NOT_FOUND:e instanceof IllegalStateException?HttpStatus.CONFLICT:HttpStatus.BAD_REQUEST;return ResponseEntity.status(status).body(Map.of("error",e.getMessage() == null ? "Yêu cầu không hợp lệ" : e.getMessage()));}
    }
}
