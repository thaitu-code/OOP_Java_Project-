package vn.id.fitnessai.service;
import tools.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.LongSupplier;
@Service @Slf4j public class GeminiAiClient {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final LongSupplier nanoTime;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration CALL_BUDGET = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 4;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.8-flash-lite}")
    private String model;
    @Autowired
    public GeminiAiClient(ObjectMapper mapper){
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }
    GeminiAiClient(ObjectMapper mapper, HttpClient client){
        this(mapper,client,System::nanoTime);
    }
    GeminiAiClient(ObjectMapper mapper, HttpClient client, LongSupplier nanoTime){
        this.mapper=mapper;
        this.client=client;
        this.nanoTime=nanoTime;
    }
    public String generate(String system,List<Map<String,Object>> messages) {
        return request(system, messages, false);
    }
    public String generateJson(String system,List<Map<String,Object>> messages) {
        return request(system, messages, true);
    }
    private String request(String system,List<Map<String,Object>> messages, boolean json) {
        if(apiKey.isBlank()||model.isBlank())
            throw new IllegalStateException("Chưa cấu hình GEMINI_API_KEY hoặc GEMINI_MODEL");
        try {
            List<Map<String,Object>> contents=new ArrayList<>();
            for(var m:messages)
                contents.add(Map.of("role",m.get("role"),"parts",List.of(Map.of("text",m.get("text")))));
            Map<String,Object> config = new HashMap<>(Map.of("maxOutputTokens",8192,"temperature",0.2));
            if (json) config.put("responseMimeType", "application/json");
            Map<String,Object> body=Map.of("systemInstruction",Map.of("parts",List.of(Map.of("text",system))),"contents",contents,"generationConfig",config);
            String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+java.net.URLEncoder.encode(model,java.nio.charset.StandardCharsets.UTF_8)+":generateContent";
            HttpResponse<String> res=sendWithRetry(endpoint, mapper.writeValueAsString(body));
            JsonNode candidates=mapper.readTree(res.body()).path("candidates");
            String finish = candidates.path(0).path("finishReason").asText("");
            if (!finish.isBlank() && !finish.equals("STOP")) throw new IllegalStateException("Gemini chưa trả phản hồi hoàn chỉnh. Hãy thử lại.");
            StringBuilder output = new StringBuilder();
            for (JsonNode part : candidates.path(0).path("content").path("parts")) if (!part.path("thought").asBoolean(false)) output.append(part.path("text").asText(""));
            String result=output.toString();
            if(result.isBlank())
                throw new IllegalStateException("Gemini trả về nội dung trống");
            return result;
        }catch(HttpTimeoutException e){
            throw new IllegalStateException("Gemini phản hồi quá lâu. Tin nhắn đã được giữ; hãy thử lại sau.",e);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini bị gián đoạn",e);
        }
        catch(IllegalStateException e){
            throw e;
        }
        catch(Exception e){
            throw new IllegalStateException("Không kết nối được Gemini: "+e.getClass().getSimpleName(),e);
        }
    }

    private HttpResponse<String> sendWithRetry(String endpoint, String body) throws java.io.IOException, InterruptedException {
        // Keep the total provider wait bounded, including backoff. A catalog lookup
        // can invoke this twice, leaving a margin within the 90-second pending-turn lease.
        long started=nanoTime.getAsLong(), deadline=started+CALL_BUDGET.toNanos();
        int payloadBytes=body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String requestId=Objects.toString(MDC.get("aiRequestId"),"-");
        for (int attempt=0;attempt<MAX_ATTEMPTS;attempt++) {
            long remaining=deadline-nanoTime.getAsLong();
            if (remaining<=0) throw new HttpTimeoutException("Provider call budget exceeded");
            HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofNanos(Math.min(REQUEST_TIMEOUT.toNanos(),remaining)))
                    .header("Content-Type","application/json").header("x-goog-api-key",apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=client.send(req,HttpResponse.BodyHandlers.ofString());
            int status=response.statusCode();
            long elapsedMillis=Duration.ofNanos(Math.max(0,nanoTime.getAsLong()-started)).toMillis();
            if(status==200){
                log.info("GEMINI_HTTP_SUCCESS requestId={} model={} attempts={} payloadBytes={} elapsedMs={}",requestId,model,attempt+1,payloadBytes,elapsedMillis);
                return response;
            }
            log.warn("GEMINI_HTTP_FAILURE requestId={} model={} http={} providerStatus={} attempt={}/{} payloadBytes={} elapsedMs={}",
                    requestId,model,status,providerStatus(response),attempt+1,MAX_ATTEMPTS,payloadBytes,elapsedMillis);
            if(attempt+1<MAX_ATTEMPTS && Set.of(502,503,504).contains(status)) {
                Duration delay=retryDelay(response,attempt);
                long retryBudget=deadline-nanoTime.getAsLong()-Duration.ofSeconds(1).toNanos();
                // Do not retry sooner than Retry-After or extend the call budget to fit it.
                if(retryBudget<=0 || delay.compareTo(Duration.ofNanos(retryBudget))>=0)
                    throw new IllegalStateException(providerError(status,attempt+1));
                pauseBeforeRetry(delay);
                continue;
            }
            throw new IllegalStateException(providerError(status,attempt+1));
        }
        throw new IllegalStateException("Dịch vụ AI tạm thời chưa sẵn sàng. Hãy thử lại sau.");
    }

    void pauseBeforeRetry(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    private Duration retryDelay(HttpResponse<String> response,int attempt) {
        Duration backoff=Duration.ofMillis((1000L<<attempt)+java.util.concurrent.ThreadLocalRandom.current().nextInt(500));
        String retryAfter=response.headers().firstValue("Retry-After").orElse("").trim();
        try {
            Duration requested;
            if(retryAfter.matches("[0-9]+")) requested=Duration.ofSeconds(Long.parseLong(retryAfter));
            else if(!retryAfter.isEmpty()) requested=Duration.between(ZonedDateTime.now(java.time.ZoneOffset.UTC),ZonedDateTime.parse(retryAfter,DateTimeFormatter.RFC_1123_DATE_TIME));
            else return backoff;
            return requested.compareTo(backoff)>0?requested:backoff;
        } catch (java.time.DateTimeException | NumberFormatException e) { return backoff; }
    }

    private String providerStatus(HttpResponse<String> response) {
        // Never log provider bodies: they can contain prompt text or account information.
        try {
            String status=mapper.readTree(response.body()).path("error").path("status").asText("");
            return status.matches("[A-Z_]{1,40}")?status:"UNKNOWN";
        } catch (RuntimeException e) { return "UNKNOWN"; }
    }

    private String providerError(int status,int attempts) {
        return switch(status) {
            case 502,503,504 -> "Gemini tạm thời quá tải hoặc gián đoạn (HTTP "+status+"). Đã thử "+attempts+" lần nhưng chưa thành công. Tin nhắn đã được giữ; hãy đợi một lát rồi bấm Gửi để thử lại.";
            case 429 -> "Gemini đang giới hạn lượt gọi hoặc đã hết hạn mức (HTTP 429). Hãy đợi một lát; quản trị viên có thể kiểm tra hạn mức nếu lỗi kéo dài.";
            case 401,403 -> "Dịch vụ AI chưa được cấp quyền truy cập Gemini (HTTP "+status+"). Vui lòng liên hệ quản trị viên để kiểm tra cấu hình.";
            case 404 -> "Không tìm thấy model Gemini đã cấu hình (HTTP 404). Vui lòng liên hệ quản trị viên.";
            case 500 -> "Gemini gặp lỗi xử lý phía dịch vụ (HTTP 500). Tin nhắn đã được giữ; hãy thử lại sau.";
            default -> "Gemini không xử lý được yêu cầu (HTTP "+status+"). Tin nhắn đã được giữ; hãy thử lại hoặc báo quản trị viên nếu lỗi kéo dài.";
        };
    }
}
