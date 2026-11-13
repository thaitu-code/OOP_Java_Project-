package vn.id.fitnessai.controller;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.id.fitnessai.service.DashboardService;
import java.security.Principal;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;
    @GetMapping("/api/dashboard/summary")
    public ResponseEntity<DashboardService.Summary> summary(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.summary(principal.getName()));
    }
    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String,String>> forbidden(SecurityException e) {return ResponseEntity.status(403).body(Map.of("message", e.getMessage()));}
}
