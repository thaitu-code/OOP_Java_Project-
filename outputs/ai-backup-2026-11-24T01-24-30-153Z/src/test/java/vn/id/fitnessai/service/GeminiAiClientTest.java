package vn.id.fitnessai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
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
        doNothing().when(client).pauseBeforeRetry(any(Duration.class));
    }
    @SuppressWarnings("unchecked") private HttpResponse<String> response(int status) {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(),(key,value)->true));
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
        verify(client).pauseBeforeRetry(any(Duration.class));
    }
    @Test void repeated503StopsAfterFourAttemptsWithAnAccurateSafeMessage() throws Exception {
        doReturn(response(503)).when(http).send(any(),any());
        String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
        assertTrue(message.contains("tạm thời"));assertTrue(message.contains("503"));
        assertFalse(message.contains("API key"));assertFalse(message.contains("PRIVATE_PROVIDER_ERROR"));
        assertTrue(message.contains("4 lần"));
        verify(http,times(4)).send(any(),any());verify(client,times(3)).pauseBeforeRetry(any(Duration.class));
    }
    @Test void quotaAndConfigurationErrorsAreNotAutomaticallyRetried() throws Exception {
        for(int status:List.of(400,401,403,404,429)) {
            clearInvocations(http,client);
            doReturn(response(status)).when(http).send(any(),any());
            String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
            assertTrue(message.contains("HTTP "+status));assertFalse(message.contains("PRIVATE_PROVIDER_ERROR"));
            verify(http).send(any(),any());verify(client,never()).pauseBeforeRetry(any(Duration.class));
        }
    }
    @Test void requestTimeoutPreservesTheFailureWithoutLaunchingAnUnboundedRetry() throws Exception {
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenThrow(new HttpTimeoutException("PRIVATE_NETWORK_DETAILS"));
        String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
        assertTrue(message.contains("phản hồi quá lâu"));assertFalse(message.contains("PRIVATE_NETWORK_DETAILS"));
        verify(http).send(any(),any());verify(client,never()).pauseBeforeRetry(any(Duration.class));
    }
    @Test void interruptedBackoffStopsAndPreservesTheThreadInterruptFlag() throws Exception {
        doReturn(response(503)).when(http).send(any(),any());
        doThrow(new InterruptedException()).when(client).pauseBeforeRetry(any(Duration.class));
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
    @Test void thirdTransientFailureCanRecoverOnTheFourthAttemptWithIncreasingBackoff() throws Exception {
        var unavailable=response(503);var success=response(200);
        doReturn(unavailable,unavailable,unavailable,success).when(http).send(any(),any());
        assertTrue(client.generateJson("Test",MESSAGES).contains("Xin chào"));
        var pauses=org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(client,times(3)).pauseBeforeRetry(pauses.capture());
        var delays=pauses.getAllValues();
        for(int i=0;i<3;i++)assertTrue(delays.get(i).toMillis()>=(1000L<<i) && delays.get(i).toMillis()<(1000L<<i)+500);
        verify(http,times(4)).send(any(),any());
    }
    @Test void retryAfterIsRespectedWhenItFitsTheBudget() throws Exception {
        var unavailable=response(503);var success=response(200);
        when(unavailable.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("5")),(key,value)->true));
        doReturn(unavailable,success).when(http).send(any(),any());
        client.generateJson("Test",MESSAGES);
        verify(client).pauseBeforeRetry(Duration.ofSeconds(5));verify(http,times(2)).send(any(),any());
    }
    @Test void retryAfterLongerThanTheBudgetDoesNotTriggerAnEarlyRetry() throws Exception {
        var unavailable=response(503);
        when(unavailable.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("60")),(key,value)->true));
        doReturn(unavailable).when(http).send(any(),any());
        String message=assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES)).getMessage();
        assertTrue(message.contains("1 lần"));verify(http).send(any(),any());
        verify(client,never()).pauseBeforeRetry(any(Duration.class));
    }
    @Test void aSlowFailedRequestCannotExtendTheThirtySecondBudget() throws Exception {
        var clock=new AtomicLong();
        var bounded=spy(new GeminiAiClient(new ObjectMapper(),http,clock::get));
        ReflectionTestUtils.setField(bounded,"apiKey","TEST_ONLY_NOT_A_REAL_KEY");
        ReflectionTestUtils.setField(bounded,"model","gemini-3.5-flash-lite");
        var unavailable=response(503);
        doAnswer(call->{clock.addAndGet(Duration.ofMillis(29500).toNanos());return unavailable;}).when(http).send(any(),any());
        assertThrows(IllegalStateException.class,()->bounded.generateJson("Test",MESSAGES));
        verify(http).send(any(),any());verify(bounded,never()).pauseBeforeRetry(any(Duration.class));
    }
    @Test void aFutureHttpDateRetryAfterCannotBeIgnoredToRetryEarly() throws Exception {
        var unavailable=response(503);
        String date=java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(2).format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        when(unavailable.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of(date)),(key,value)->true));
        doReturn(unavailable).when(http).send(any(),any());
        assertThrows(IllegalStateException.class,()->client.generateJson("Test",MESSAGES));
        verify(http).send(any(),any());verify(client,never()).pauseBeforeRetry(any(Duration.class));
    }
    @Test void malformedRetryAfterDoesNotBreakRecovery() throws Exception {
        var unavailable=response(503);var success=response(200);
        when(unavailable.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After",List.of("not-a-date")),(key,value)->true));
        doReturn(unavailable,success).when(http).send(any(),any());
        assertTrue(client.generateJson("Test",MESSAGES).contains("Xin chào"));
        verify(client).pauseBeforeRetry(any(Duration.class));verify(http,times(2)).send(any(),any());
    }
}
