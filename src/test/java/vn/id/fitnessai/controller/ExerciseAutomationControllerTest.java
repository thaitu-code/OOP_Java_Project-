package vn.id.fitnessai.controller;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.dto.ExerciseLevelResponse;
import vn.id.fitnessai.service.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {ExerciseLevelController.class, ExerciseMediaController.class})
@Import(SecurityConfig.class)
class ExerciseAutomationControllerTest {
    @TempDir java.nio.file.Path directory;
    @Autowired MockMvc mvc;
    @MockitoBean ExerciseLevelService levels;
    @MockitoBean ExerciseMediaService media;
    @MockitoBean CustomUserDetailsService accounts;
    @MockitoBean EntityManagerFactory emf;
    final String body = "{\"name\":\"Tùy chọn\",\"sets\":3,\"reps\":null,\"secondsPerSet\":30,\"restSeconds\":60,\"estimatedSeconds\":210}";
    @Test void adminCanCreateLevelAndReturnsCreated() throws Exception {
        when(levels.save(eq(1), isNull(), any())).thenReturn(new ExerciseLevelResponse(2, "Tùy chọn", 3, null, 30, 60, 210, true));
        mvc.perform(post("/admin/exercises/1/levels").with(user("a").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.estimatedSeconds").value(210));
    }
    @Test void regularUserCannotCreateOrUpload() throws Exception {
        mvc.perform(post("/admin/exercises/1/levels").with(user("u")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/exercises/1/media").file(new MockMultipartFile("file", "test.gif", "image/gif", new byte[]{1}))
                .with(user("u")).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(levels, media);
    }
    @Test void missingCsrfBlocksAdminWrites() throws Exception {
        mvc.perform(post("/admin/exercises/1/levels").with(user("a").roles("ADMIN"))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(levels);
    }
    @Test void invalidLevelIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/admin/exercises/1/levels").with(user("a").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(body.replace("\"sets\":3", "\"sets\":0"))).andExpect(status().isBadRequest());
        verifyNoInteractions(levels);
    }
    @Test void adminCanUploadMultipartFile() throws Exception {
        mvc.perform(multipart("/admin/exercises/1/media").file(new MockMultipartFile("file", "test.gif", "image/gif", new byte[]{1}))
                .with(user("a").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        verify(media).attach(eq(1), any());
    }
    @Test void mediaReadRequiresLogin() throws Exception {
        mvc.perform(get("/exercise-media/00000000-0000-0000-0000-000000000000.gif"))
                .andExpect(status().is3xxRedirection()); verifyNoInteractions(media);
    }
    @Test void authenticatedUserCanReadGifWithExplicitTypeAndNoSniff() throws Exception {
        String name = "00000000-0000-0000-0000-000000000000.gif";
        var path = java.nio.file.Files.write(directory.resolve(name), new byte[]{1, 2, 3, 4});
        when(media.read(name)).thenReturn(path);
        mvc.perform(get("/exercise-media/" + name).with(user("u")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/gif"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
    @Test void videoSupportsRangeRequestsForSeeking() throws Exception {
        String name = "00000000-0000-0000-0000-000000000000.mp4";
        var path = java.nio.file.Files.write(directory.resolve(name), new byte[]{1, 2, 3, 4, 5, 6});
        when(media.read(name)).thenReturn(path);
        mvc.perform(get("/exercise-media/" + name).header("Range", "bytes=0-2").with(user("u")))
                .andExpect(status().isPartialContent()).andExpect(content().bytes(new byte[]{1, 2, 3}));
    }
}
