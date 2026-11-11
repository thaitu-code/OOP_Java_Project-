package vn.id.fitnessai.service;
import tools.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
@Service public class GeminiAiClient {
    private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;
    public GeminiAiClient(ObjectMapper mapper){
        this.mapper=mapper;
    }
    public String generate(String system,List<Map<String,Object>> messages) {
        System.out.println("Gemini API key loaded: "
                + (apiKey != null && !apiKey.isBlank()));
        System.out.println("Gemini model: " + model);
        if(apiKey.isBlank()||model.isBlank())
            throw new IllegalStateException("Chưa cấu hình GEMINI_API_KEY hoặc GEMINI_MODEL");
        try {
            List<Map<String,Object>> contents=new ArrayList<>();
            for(var m:messages)
                contents.add(Map.of("role",m.get("role"),"parts",List.of(Map.of("text",m.get("text")))));
            Map<String,Object> body=Map.of("systemInstruction",Map.of("parts",List.of(Map.of("text",system))),"contents",contents,"generationConfig",Map.of("maxOutputTokens",3500,"temperature",0.5));
            String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+java.net.URLEncoder.encode(model,java.nio.charset.StandardCharsets.UTF_8)+":generateContent";
            HttpRequest req=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(25)).header("Content-Type","application/json").header("x-goog-api-key",apiKey).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> res=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(res.statusCode()!=200)
                throw new IllegalStateException("Gemini HTTP "+res.statusCode()+" (kiểm tra API key, model và hạn mức)");
            JsonNode candidates=mapper.readTree(res.body()).path("candidates");
            String result=candidates.path(0).path("content").path("parts").path(0).path("text").asText("");
            if(result.isBlank())
                throw new IllegalStateException("Gemini trả về nội dung trống");
            return result;
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
}
