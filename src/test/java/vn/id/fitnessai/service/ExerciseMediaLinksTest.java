package vn.id.fitnessai.service;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.id.fitnessai.dto.ExerciseRequest;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExerciseMediaLinksTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://cdn.example.test/move.gif", "https://cdn.example.test/MOVE.JPG?sig=a%2Bb#pose",
            "https://cdn.example.test/image.png", "http://cdn.example.test/move.webp",
            "https://cdn.example.test/video.mp4?token=test", "https://cdn.example.test/video.webm",
            "/exercise-media/00000000-0000-0000-0000-000000000000.gif"})
    void acceptsDirectSupportedUrlsWithoutConnectingToTheRemoteHost(String value) {
        assertEquals(value, ExerciseMediaLinks.normalize("  " + value + "  "));
    }
    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:image/gif;base64,R0lG", "file:///tmp/photo.gif",
            "//cdn.example.test/photo.gif", "https://youtube.com/watch?v=test", "https://cdn.example.test/image.svg",
            "https://name:secret@example.test/image.gif", "/exercise-media/../../config.gif", "https:relative/image.gif"})
    void rejectsPagesUnsafeSchemesCredentialsAndArbitraryLocalPaths(String value) {
        assertThrows(IllegalArgumentException.class, () -> ExerciseMediaLinks.normalize(value));
    }
    @Test void blankMeansClearAndOversizeIsRejected() {
        assertNull(ExerciseMediaLinks.normalize(null)); assertNull(ExerciseMediaLinks.normalize("  "));
        assertThrows(IllegalArgumentException.class, () -> ExerciseMediaLinks.normalize("https://cdn.example.test/" + "a".repeat(500) + ".gif"));
    }
    @Test void convertsGithubFilePageToRawBinaryUrlWithoutNetworkRequest() {
        assertEquals("https://raw.githubusercontent.com/team/assets/main/exercises/move.gif?raw=true",
                ExerciseMediaLinks.normalize("https://github.com/team/assets/blob/main/exercises/move.gif?raw=true"));
        assertEquals("https://raw.githubusercontent.com/team/assets/abc123/exercises/move.mp4",
                ExerciseMediaLinks.normalize("https://github.com/team/assets/raw/abc123/exercises/move.mp4"));
    }
    @Test void normalizedGithubUrlMustStillFitTheDatabaseColumn() {
        String prefix = "https://github.com/team/assets/blob/main/";
        String value = prefix + "a".repeat(500 - prefix.length() - 4) + ".gif";
        assertEquals(500, value.length());
        assertThrows(IllegalArgumentException.class, () -> ExerciseMediaLinks.normalize(value));
    }

    ExerciseRequest request() {
        var r = new ExerciseRequest(); r.setExerciseName("Test"); r.setCategoryId(2);
        r.setMetValue(BigDecimal.ONE); r.setMinDurationMinutes(BigDecimal.ONE); return r;
    }
    @Test void createPersistsDirectUrlAndReturnsItWithoutAddingStorageDependencies() {
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            var exercises = mock(ExerciseRepository.class); var categories = mock(ExerciseCategoryRepository.class);
            var service = new ExerciseService(exercises, categories, mock(WorkoutDetailRepository.class), validator.getValidator());
            when(categories.findById(2)).thenReturn(Optional.of(new ExerciseCategory("Test")));
            when(exercises.save(any())).thenAnswer(call -> call.getArgument(0));
            var request = request(); request.setMediaUrl(" https://cdn.example.test/exercise.gif ");
            assertEquals("https://cdn.example.test/exercise.gif", service.createExercise(request).getMediaUrl());
        }
    }
    @Test void invalidDirectUrlFailsBeforeAnyDatabaseLookup() {
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            var exercises = mock(ExerciseRepository.class); var categories = mock(ExerciseCategoryRepository.class);
            var details = mock(WorkoutDetailRepository.class);
            var service = new ExerciseService(exercises, categories, details, validator.getValidator());
            var request = request(); request.setMediaUrl("javascript:alert(1)");
            assertThrows(IllegalArgumentException.class, () -> service.createExercise(request));
            verifyNoInteractions(exercises, categories, details);
        }
    }
    @Test void oldRequestPreservesUploadedMediaWhileExplicitBlankClearsIt() {
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            var exercises = mock(ExerciseRepository.class); var categories = mock(ExerciseCategoryRepository.class);
            var service = new ExerciseService(exercises, categories, mock(WorkoutDetailRepository.class), validator.getValidator());
            var category = new ExerciseCategory("Group"); ReflectionTestUtils.setField(category, "categoryId", 2);
            var exercise = new Exercise("Test", BigDecimal.ONE, BigDecimal.ONE, category);
            String uploaded = "/exercise-media/00000000-0000-0000-0000-000000000000.gif";
            exercise.setMediaUrl(uploaded);
            when(exercises.findByIdForUpdate(1)).thenReturn(Optional.of(exercise));
            when(categories.findById(2)).thenReturn(Optional.of(category));
            when(exercises.save(exercise)).thenReturn(exercise);
            var request = request();
            assertEquals(uploaded, service.updateExercise(1, request).orElseThrow().getMediaUrl());
            request.setMediaUrl("");
            assertNull(service.updateExercise(1, request).orElseThrow().getMediaUrl());
            request.setMediaUrl("https://cdn.example.test/move.mp4?version=2");
            assertEquals(request.getMediaUrl(), service.updateExercise(1, request).orElseThrow().getMediaUrl());
        }
    }
}
