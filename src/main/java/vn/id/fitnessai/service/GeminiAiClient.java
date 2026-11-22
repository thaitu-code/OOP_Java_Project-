package vn.id.fitnessai.service;
import tools.jackson.databind.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Service public class GeminiAiClient {
    private final ObjectMapper mapper;
    private final HttpClient client;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration CALL_BUDGET = Duration.ofSeconds(30);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;
    @Autowired
    public GeminiAiClient(ObjectMapper mapper){
        this(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build());
    }
    GeminiAiClient(ObjectMapper mapper, HttpClient client){
        this.mapper=mapper;
        this.client=client;
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
        // Two provider calls at most, within one bounded chat attempt. A catalog lookup
        // can invoke this twice, still below the store's 90-second pending-turn lease.
        long deadline=System.nanoTime()+CALL_BUDGET.toNanos();
        for (int attempt=0;attempt<2;attempt++) {
            long remaining=deadline-System.nanoTime();
            if (remaining<=0) throw new HttpTimeoutException("Provider call budget exceeded");
            HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofNanos(Math.min(REQUEST_TIMEOUT.toNanos(),remaining)))
                    .header("Content-Type","application/json").header("x-goog-api-key",apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=client.send(req,HttpResponse.BodyHandlers.ofString());
            int status=response.statusCode();
            if(status==200)return response;
            if(attempt==0 && Set.of(502,503,504).contains(status)) {
                pauseBeforeRetry();
                continue;
            }
            throw new IllegalStateException(providerError(status));
        }
        throw new IllegalStateException("Dịch vụ AI tạm thời chưa sẵn sàng. Hãy thử lại sau.");
    }

    void pauseBeforeRetry() throws InterruptedException {
        Thread.sleep(1000+java.util.concurrent.ThreadLocalRandom.current().nextInt(500));
    }

    private String providerError(int status) {
        return switch(status) {
            case 502,503,504 -> "Gemini tạm thời quá tải hoặc gián đoạn (HTTP "+status+"). Đã thử lại nhưng chưa thành công. Tin nhắn đã được giữ; hãy đợi một lát rồi bấm Gửi để thử lại.";
            case 429 -> "Gemini đang giới hạn lượt gọi hoặc đã hết hạn mức (HTTP 429). Hãy đợi một lát; quản trị viên có thể kiểm tra hạn mức nếu lỗi kéo dài.";
            case 401,403 -> "Dịch vụ AI chưa được cấp quyền truy cập Gemini (HTTP "+status+"). Vui lòng liên hệ quản trị viên để kiểm tra cấu hình.";
            case 404 -> "Không tìm thấy model Gemini đã cấu hình (HTTP 404). Vui lòng liên hệ quản trị viên.";
            case 500 -> "Gemini gặp lỗi xử lý phía dịch vụ (HTTP 500). Tin nhắn đã được giữ; hãy thử lại sau.";
            default -> "Gemini không xử lý được yêu cầu (HTTP "+status+"). Tin nhắn đã được giữ; hãy thử lại hoặc báo quản trị viên nếu lỗi kéo dài.";
        };
    }
}
