package vn.id.fitnessai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeminiAiClientTest {
    private final HttpClient http=mock(HttpClient.class);
    private final GeminiAiClient client=spy(new GeminiAiClient(new ObjectMapper(),http));
    private static final List<Map<String,Object>> MESSAGES=List.of(Map.of("role","user","text","Tạo lịch tập"));
    @BeforeEach void setup() throws Exception {
        ReflectionTestUtils.setField(client,"apiKey","TEST_ONLY_NOT_A_REAL_KEY");
        ReflectionTestUtils.setField(client,"model","gemini-2.5-flash");
        doNothing().when(client).pauseBeforeRetry();
    }
    @SuppressWarnings("unchecked") private HttpResponse<String> response(int status) {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        String body=new ObjectMapper().writeValueAsString(Map.of("candidates",List.of(Map.of("finishReason","STOP",
                "content",Map.of("parts",List.of(Map.of("thought",true,"text","PRIVATE_THOUGHT"),
                        Map.of("text","{\"type\":\"ANSWER\",\"reply\":\"Xin chào\",\"actions\":[]}")))))));
        when(response.body()).thenReturn(status==200 ? body : "PRIVATE_PROVIDER_ERROR");
        return response;
    }
    @Test void transient503RetriesOnceAndReturnsOnlyTheCompletedAnswer() throws Exception {
        doReturn(response(503),response(200)).when(http).send(any(),any());
        String answer=client.generateJson("Test instructions",MESSAGES);
        assertTrue(answer.contains("Xin chào"));assertFalse(answer.contains("PRIVATE_THOUGHT"));
        verify(http,times(2)).send(argThat(req -> req.timeout().orElseThrow().toSeconds()<=25),any());
        verify(client).pauseBeforeRetry();
    }
    @Test void repeated503StopsAfterTwoAttemptsWithAnAccurateSafeMessage() throws Exception {
        doReturn(response(503)).when(http).send(any(),any());
        String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
        assertTrue(message.contains("tạm thời"));assertTrue(message.contains("503"));
        assertFalse(message.contains("API key"));assertFalse(message.contains("PRIVATE_PROVIDER_ERROR"));
        verify(http,times(2)).send(any(),any());verify(client).pauseBeforeRetry();
    }
    @Test void quotaAndConfigurationErrorsAreNotAutomaticallyRetried() throws Exception {
        for(int status:List.of(400,401,403,404,429)) {
            clearInvocations(http,client);
            doReturn(response(status)).when(http).send(any(),any());
            String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
            assertTrue(message.contains("HTTP "+status));assertFalse(message.contains("PRIVATE_PROVIDER_ERROR"));
            verify(http).send(any(),any());verify(client,never()).pauseBeforeRetry();
        }
    }
    @Test void requestTimeoutPreservesTheFailureWithoutLaunchingAnUnboundedRetry() throws Exception {
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new HttpTimeoutException("PRIVATE_NETWORK_DETAILS"));
        String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
        assertTrue(message.contains("phản hồi quá lâu"));assertFalse(message.contains("PRIVATE_NETWORK_DETAILS"));
        verify(http).send(any(),any());verify(client,never()).pauseBeforeRetry();
    }
    @Test void interruptedBackoffStopsAndPreservesTheThreadInterruptFlag() throws Exception {
        doReturn(response(503)).when(http).send(any(),any());
        doThrow(new InterruptedException()).when(client).pauseBeforeRetry();
        try {
            assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES));
            assertTrue(Thread.currentThread().isInterrupted());verify(http).send(any(),any());
        } finally { Thread.interrupted(); }
    }
    @Test void incompleteResponseIsNotReturnedAsAUsableProposal() throws Exception {
        var response=response(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}");
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        assertTrue(assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage().contains("chưa trả phản hồi hoàn chỉnh"));
        verify(http).send(any(),any());
    }
}
