package vn.id.fitnessai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.id.fitnessai.dto.ExerciseResponse;
import vn.id.fitnessai.repository.ExerciseRepository;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class ExerciseMediaService {
    private final ExerciseRepository exercises;
    private final TransactionTemplate transaction;
    private final Path directory;
    private final long maxBytes;

    public ExerciseMediaService(ExerciseRepository exercises, PlatformTransactionManager manager,
            @Value("${fitness.exercise.media-directory:uploads/exercises}") String directory,
            @Value("${fitness.exercise.media-max-bytes:52428800}") long maxBytes) {

        this.exercises = exercises; this.transaction = new TransactionTemplate(manager);
        this.directory = Path.of(directory).toAbsolutePath().normalize(); this.maxBytes = maxBytes;
    }

    public ExerciseResponse attach(Integer exerciseId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Hãy chọn GIF hoặc video MP4/WebM");

        if (file.getSize() > maxBytes) throw new ResponseStatusException(HttpStatus.valueOf(413), "Tệp vượt giới hạn tải lên");

        Files.createDirectories(directory);
        Path stored = null;

        try (var input = new PushbackInputStream(file.getInputStream(), 12)) {
            byte[] header = input.readNBytes(12);

            String extension;

            if (header.length >= 6 && (new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("GIF89a")
                    || new String(header, 0, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("GIF87a"))) extension = "gif";

            else if (header.length >= 12 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') extension = "mp4";

            else if (header.length >= 4 && header[0] == 0x1a && header[1] == 0x45 && header[2] == (byte) 0xdf && header[3] == (byte) 0xa3) extension = "webm";

            else throw new IllegalArgumentException("Nội dung tệp không phải GIF, MP4 hoặc WebM được hỗ trợ");

            input.unread(header);
            String name = UUID.randomUUID() + "." + extension;
            stored = directory.resolve(name);

            try (var output = Files.newOutputStream(stored, StandardOpenOption.CREATE_NEW)) {
                byte[] buffer = new byte[8192]; long count = 0; int length;
                while ((length = input.read(buffer)) != -1) {
                    count += length;
                    if (count > maxBytes) throw new ResponseStatusException(HttpStatus.valueOf(413), "Tệp vượt giới hạn tải lên");
                    output.write(buffer, 0, length);
                }
            }

            // Đọc/ghi tệp xong mới khóa DB, không giữ khóa khi truyền file.
            return transaction.execute(status -> {
                var exercise = exercises.findByIdForUpdate(exerciseId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
                exercise.setMediaUrl("/exercise-media/" + name);
                return ExerciseResponse.from(exercises.saveAndFlush(exercise));
            });
        } catch (IOException | RuntimeException failure) {
            if (stored != null) {
                try { Files.deleteIfExists(stored); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
        // Giữ file cũ vì snapshot lịch sử có thể vẫn tham chiếu; không ghi đè theo tên người dùng.
    }

    public Path read(String name) {

        if (!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(gif|mp4|webm)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        Path path = directory.resolve(name).normalize();

        if (!path.getParent().equals(directory) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        return path;
    }
}
