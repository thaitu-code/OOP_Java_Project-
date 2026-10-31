package vn.id.fitnessai.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.web.server.ResponseStatusException;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.ExerciseRepository;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExerciseMediaServiceTest {
    @TempDir Path directory;
    final ExerciseRepository exercises = mock(ExerciseRepository.class);
    final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    ExerciseMediaService service;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new ExerciseMediaService(exercises, manager, directory.toString(), 1024);
    }
    MockMultipartFile gif() {
        return new MockMultipartFile("file", "../../unsafe.html", "text/html",
                Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"));
    }
    @Test void storesUnderRandomSafeNameAndKeepsPreviouslyStoredFile() throws Exception {
        var exercise = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, new ExerciseCategory("Test"));
        when(exercises.findByIdForUpdate(1)).thenReturn(Optional.of(exercise));
        when(exercises.saveAndFlush(exercise)).thenReturn(exercise);
        String first = service.attach(1, gif()).getMediaUrl();
        String second = service.attach(1, gif()).getMediaUrl();
        assertNotEquals(first, second); assertTrue(first.matches("/exercise-media/[0-9a-f-]+\\.gif"));
        assertTrue(Files.exists(service.read(first.substring(first.lastIndexOf('/') + 1)))) ;
        assertTrue(Files.exists(service.read(second.substring(second.lastIndexOf('/') + 1))));
        verify(manager, times(2)).commit(any());
    }
    @Test void rejectsHtmlEvenWithVideoFilenameAndMimeType() {
        assertThrows(IllegalArgumentException.class, () -> service.attach(1,
                new MockMultipartFile("file", "movie.mp4", "video/mp4", "<html>not video</html>".getBytes())));
        verifyNoInteractions(exercises);
    }
    @Test void rejectsEmptyAndOversizeFiles() {
        assertThrows(IllegalArgumentException.class, () -> service.attach(1, new MockMultipartFile("file", new byte[0])));
        assertThrows(ResponseStatusException.class, () -> service.attach(1, new MockMultipartFile("file", new byte[1025])));
        verifyNoInteractions(exercises);
    }
    @Test void enforcesStreamingLimitEvenWhenReportedSizeIsSmaller() throws Exception {
        service = new ExerciseMediaService(exercises, manager, directory.toString(), 20);
        var misleading = spy(gif()); doReturn(1L).when(misleading).getSize();
        assertThrows(ResponseStatusException.class, () -> service.attach(1, misleading));
        try (var paths = Files.list(directory)) { assertEquals(0, paths.count()); }
        verifyNoInteractions(exercises);
    }
    @Test void missingExerciseRollsBackAndRemovesUnattachedFile() throws Exception {
        when(exercises.findByIdForUpdate(1)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.attach(1, gif()));
        try (var paths = Files.list(directory)) { assertEquals(0, paths.count()); }
        verify(manager).rollback(any());
    }
    @Test void invalidAndMissingMediaPathsAreNotReadable() {
        for (String name : List.of("../application.properties", "x.html", "123.gif", UUID.randomUUID() + ".mp4"))
            assertThrows(ResponseStatusException.class, () -> service.read(name));
    }
}
