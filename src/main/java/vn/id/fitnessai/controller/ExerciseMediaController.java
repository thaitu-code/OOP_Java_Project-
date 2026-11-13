package vn.id.fitnessai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import vn.id.fitnessai.dto.ExerciseResponse;
import vn.id.fitnessai.service.ExerciseMediaService;
import java.io.IOException;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class ExerciseMediaController {
    private final ExerciseMediaService service;

    @PostMapping(value = "/admin/exercises/{exerciseId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExerciseResponse upload(@PathVariable Integer exerciseId, @RequestParam("file") MultipartFile file) throws IOException {
        return service.attach(exerciseId, file);
    }

    @GetMapping("/exercise-media/{name}")
    public ResponseEntity<Resource> media(@PathVariable String name) throws IOException {
        var path = service.read(name);
        String type = name.endsWith(".gif") ? "image/gif" : name.endsWith(".mp4") ? "video/mp4" : "video/webm";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(type)).contentLength(java.nio.file.Files.size(path))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, max-age=3600")
                .body(new FileSystemResource(path));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge() {
        return ResponseEntity.status(413).body(Map.of("message", "Tệp vượt giới hạn tải lên"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> storageError() {
        return ResponseEntity.internalServerError().body(Map.of("message", "Không lưu được tệp hướng dẫn. Hãy thử lại."));
    }
}
