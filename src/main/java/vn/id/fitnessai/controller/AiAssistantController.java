package vn.id.fitnessai.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.service.AiAssistantService;
import java.security.Principal;
import java.util.*;
@Controller @RequiredArgsConstructor public class AiAssistantController {
 private final AiAssistantService service;
 @GetMapping("/ai") public String page(){return "ai";}
 @RestController @RequestMapping("/api/ai") @RequiredArgsConstructor public static class Api {
  private final AiAssistantService service;
  private final vn.id.fitnessai.service.AiWeeklyWorkoutService weekly;
  private final vn.id.fitnessai.service.AiActionService actions;
  private final vn.id.fitnessai.service.AiPlanApplyService apply;
  @PostMapping("/recommendations/{id}/log-today") public Map<String,Object> logToday(Principal p,@PathVariable Long id){return apply.logToday(p.getName(),id);}
  @PostMapping("/recommendations/{id}/apply-workout") public Map<String,Object> applyWorkout(Principal p,@PathVariable Long id){return apply.applyWorkout(p.getName(),id);}
  @PostMapping("/recommendations/{id}/start-today-workout") public Map<String,Object> startWorkout(Principal p,@PathVariable Long id){return apply.startTodayWorkout(p.getName(),id);}
  public record ActionRequest(String instruction) {}
  @PostMapping("/actions") public vn.id.fitnessai.service.AiActionService.ActionResponse action(Principal p,@RequestBody ActionRequest r){return actions.execute(p.getName(),r.instruction());}
  @PostMapping("/weekly-workout") public AiAssistantService.PlanResult weekly(Principal p,@RequestBody vn.id.fitnessai.service.AiWeeklyWorkoutService.WeeklyRequest request){return weekly.generate(p.getName(),request);}
  public record ChatRequest(String sessionId,String content) {}
  @GetMapping("/chat") public AiAssistantService.ChatPage history(Principal p,@RequestParam(required=false) Long before){return service.history(p.getName(),before);}
  @PostMapping("/chat") public AiAssistantService.ChatResult send(Principal p,@RequestBody ChatRequest req){return service.send(p.getName(),req.sessionId(),req.content());}
  @DeleteMapping("/chat/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(Principal p,@PathVariable Long id){service.delete(p.getName(),id);}
  @GetMapping("/recommendations") public List<AiAssistantService.RecommendationView> recs(Principal p){return service.recommendations(p.getName());}
  @PostMapping("/recommendations") public AiAssistantService.PlanResult create(Principal p,@RequestBody AiAssistantService.PlanRequest request){return service.create(p.getName(),request);}
  @GetMapping("/recommendations/{id}") public Map<String,Object> detail(Principal p,@PathVariable Long id){return service.detail(p.getName(),id);}
  @PatchMapping("/recommendations/{id}/read") public AiAssistantService.RecommendationView read(Principal p,@PathVariable Long id){return service.read(p.getName(),id);}
  @ExceptionHandler({IllegalArgumentException.class,SecurityException.class,NoSuchElementException.class,IllegalStateException.class}) public ResponseEntity<Map<String,String>> error(Exception e){HttpStatus status=e instanceof SecurityException?HttpStatus.FORBIDDEN:e instanceof NoSuchElementException?HttpStatus.NOT_FOUND:e instanceof IllegalStateException?HttpStatus.SERVICE_UNAVAILABLE:HttpStatus.BAD_REQUEST;return ResponseEntity.status(status).body(Map.of("error",e.getMessage()));}
 }
}
