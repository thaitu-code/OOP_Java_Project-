package vn.id.fitnessai.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import vn.id.fitnessai.config.SecurityConfig;
import vn.id.fitnessai.service.*;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.junit.jupiter.api.Assertions.*;

@WebMvcTest(AiAssistantController.Api.class) @Import(SecurityConfig.class)
class AiConversationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AiAssistantService assistant;
    @MockitoBean AiConversationService conversation;
    @MockitoBean AiConversationStore store;
    @MockitoBean AiDraftService drafts;
    @MockitoBean AiNotificationService notifications;
    @MockitoBean AiScheduleService schedule;
    @MockitoBean AiAccess access;
    @MockitoBean AiPlanApplyService apply;
    @MockitoBean CustomUserDetailsService details;
    @Test void chatRequiresLogin() throws Exception { mvc.perform(get("/api/ai/chat")).andExpect(status().is3xxRedirection()); }
    @Test void confirmationRequiresCsrf() throws Exception {
        mvc.perform(post("/api/ai/proposals/"+UUID.randomUUID()+"/confirm").with(user("tester"))).andExpect(status().isForbidden());verifyNoInteractions(drafts);
    }
    @Test void chatIsTheOnlyMessageEntryPoint() throws Exception {
        UUID request=UUID.randomUUID();mvc.perform(post("/api/ai/chat").with(user("tester")).with(csrf()).contentType("application/json")
                .content("{\"sessionId\":\"session123\",\"content\":\"Tôi vừa cân được 65 kg\",\"requestId\":\""+request+"\"}" )).andExpect(status().isOk());
        verify(conversation).send("tester","session123","Tôi vừa cân được 65 kg",request);verifyNoInteractions(apply,drafts);
    }
    @Test void legacyActionAndGenerationEndpointsCannotWrite() throws Exception {
        mvc.perform(post("/api/ai/actions").with(user("tester")).with(csrf()).contentType("application/json").content("{\"instruction\":\"Đổi cân nặng\"}")).andExpect(status().isGone());
        mvc.perform(post("/api/ai/weekly-workout").with(user("tester")).with(csrf())).andExpect(status().isGone());verifyNoInteractions(conversation,apply,drafts);
    }
    @Test void ownershipFailureIsForbidden() throws Exception {
        when(drafts.decide(anyString(),any(),eq(true))).thenThrow(new SecurityException("Đề xuất không thuộc bạn"));
        mvc.perform(post("/api/ai/proposals/"+UUID.randomUUID()+"/confirm").with(user("tester")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void contextSurvivesPageReloadWithinTheSameLogin() throws Exception {
        var session=new MockHttpSession();mvc.perform(get("/api/ai/chat/session").session(session).with(user("tester"))).andExpect(status().isOk());
        Object first=session.getAttribute("fitness-ai-context");assertNotNull(first);
        mvc.perform(get("/api/ai/chat/session").session(session).with(user("tester"))).andExpect(status().isOk());assertEquals(first,session.getAttribute("fitness-ai-context"));
    }
}
