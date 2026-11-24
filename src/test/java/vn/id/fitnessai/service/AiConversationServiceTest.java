package vn.id.fitnessai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiConversationServiceTest {
    private final GeminiAiClient gemini=mock(GeminiAiClient.class);
    private final AiContextService context=mock(AiContextService.class);
    private final AiConversationStore store=mock(AiConversationStore.class);
    private final AiDraftEngine engine=mock(AiDraftEngine.class);
    private final AiDraftService decisions=mock(AiDraftService.class);
    private final AiConversationService service=new AiConversationService(gemini,new ObjectMapper(),context,store,engine,decisions);
    private final UUID request=UUID.randomUUID(),turn=UUID.randomUUID();
    @BeforeEach void setup(){
        when(store.begin(eq("tester"),eq("session123"),anyString(),eq(request))).thenReturn(new AiConversationStore.Ticket(turn,true));
        when(store.pending("tester","session123")).thenReturn(List.of());when(store.history("tester","session123",turn)).thenReturn(List.of());
        when(context.context("tester")).thenReturn(Map.of("profile",Map.of("weightKg",68)));when(engine.baseFingerprint("tester")).thenReturn("base");
    }
    @Test void ordinaryQuestionAndHypotheticalProduceNoDraftOrBusinessWrite(){
        when(gemini.generateJson(anyString(),anyList())).thenReturn("{\"type\":\"ANSWER\",\"reply\":\"Đây là một tình huống giả định.\",\"actions\":[]}");
        service.send("tester","session123","Nếu tôi nặng 65 kg thì sao?",request);
        verify(engine,never()).prepare(any(),any());verifyNoInteractions(decisions);verify(store).finish(eq("tester"),eq(turn),anyString(),eq(List.of()),eq("base"));
    }
    @Test void factualWeightStatementIsPreparedAndNotExecuted(){
        when(gemini.generateJson(anyString(),anyList())).thenReturn("{\"type\":\"PROPOSE\",\"reply\":\"Bạn có muốn cập nhật cân nặng?\",\"actions\":[{\"kind\":\"PROFILE_UPDATE\",\"payload\":{\"changes\":{\"weightKg\":65}}}]}");
        when(engine.prepare(eq("tester"),any())).thenReturn(new AiDraftEngine.Prepared("PROFILE_UPDATE","{}","68 → 65","hash"));
        service.send("tester","session123","Hôm nay tôi vừa cân được 65 kg",request);
        verify(engine).prepare(eq("tester"),argThat(n->n.path("payload").path("changes").path("weightKg").asInt()==65));
        verifyNoInteractions(decisions);verify(engine,never()).execute(any(),any());
    }
    @Test void followUpReceivesTheCompletedConversationAndCurrentMessageOnce(){
        when(store.history("tester","session123",turn)).thenReturn(List.of(Map.of("role","user","text","Lập lịch"),Map.of("role","model","text","Bạn tập mấy ngày?")));
        when(gemini.generateJson(anyString(),anyList())).thenReturn("{\"type\":\"CLARIFY\",\"reply\":\"Bạn có thiết bị gì?\",\"actions\":[]}");
        service.send("tester","session123","4 ngày",request);
        verify(gemini).generateJson(anyString(),argThat(p->p.size()==3&&p.get(2).get("text").toString().contains("4 ngày")&&p.get(1).get("text").equals("Bạn tập mấy ngày?")));
    }
    @Test void malformedProviderDataKeepsAFailedTurnAndDoesNotPrepareWrites(){
        when(gemini.generateJson(anyString(),anyList())).thenReturn("not-json");service.send("tester","session123","Xin chào",request);
        verify(store).fail(eq("tester"),eq(turn),anyString());verify(engine,never()).prepare(any(),any());verifyNoInteractions(decisions);
    }
    @Test void duplicateRequestDoesNotCallProviderAgain(){
        when(store.begin(eq("tester"),eq("session123"),anyString(),eq(request))).thenReturn(new AiConversationStore.Ticket(turn,false));
        service.send("tester","session123","Xin chào",request);verifyNoInteractions(gemini,engine,context,decisions);
    }
    @Test void ambiguousConsentWithTwoProposalsAsksWhichOne(){
        var p=new AiConversationStore.Proposal(UUID.randomUUID(),"PROFILE_UPDATE","preview","PENDING",null,null,null);
        when(store.pending("tester","session123")).thenReturn(List.of(p,p));service.send("tester","session123","Đồng ý",request);
        verifyNoInteractions(decisions,gemini);verify(store).finish(eq("tester"),eq(turn),contains("nhiều đề xuất"),eq(List.of()),eq(""));
    }
    @Test void missingCatalogItemCanUseOneBoundedServerLookup(){
        when(gemini.generateJson(anyString(),anyList())).thenReturn("{\"type\":\"LOOKUP\",\"query\":{\"catalog\":\"foods\",\"keyword\":\"cơm\"}}",
                "{\"type\":\"CLARIFY\",\"reply\":\"Bạn ăn bữa nào?\",\"actions\":[]}");
        when(context.lookup(eq("tester"),any())).thenReturn(Map.of("foods",List.of(Map.of("id",1200,"name","Cơm"))));
        service.send("tester","session123","Tôi vừa ăn cơm",request);verify(context).lookup(eq("tester"),any());verify(gemini,times(2)).generateJson(anyString(),anyList());
        verify(engine,never()).prepare(any(),any());
    }
}
