package vn.id.fitnessai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiDraftServiceTest {
    private final AiAccess access=mock(AiAccess.class);
    private final AiActionDraftRepository drafts=mock(AiActionDraftRepository.class);
    private final AiChatMessageRepository messages=mock(AiChatMessageRepository.class);
    private final AiDraftEngine engine=mock(AiDraftEngine.class);
    private final AiConversationStore store=mock(AiConversationStore.class);
    private final AiDraftService service=new AiDraftService(access,drafts,messages,engine,store,new ObjectMapper(),mock(jakarta.persistence.EntityManager.class));
    private final AiActionDraft draft=new AiActionDraft();
    @BeforeEach void setup(){
        var user=new User();user.setId(7L);when(access.lock("tester")).thenReturn(user);draft.setUser(user);draft.setKind("PROFILE_UPDATE");
        draft.setPayload("{\"changes\":{\"weightKg\":65}}");draft.setFingerprint("same");draft.setPreview("Cân nặng 68 → 65");draft.setSessionId("session123");
        when(drafts.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(engine.fingerprint(eq("tester"),eq("PROFILE_UPDATE"),any())).thenReturn("same");
        when(engine.execute("tester",draft)).thenReturn(new AiDraftEngine.Execution("Đã lưu",null));
    }
    @Test void repeatedConfirmationExecutesExactlyOnceAndDoesNotCallTheModel(){
        service.decide("tester",draft.getId(),true);service.decide("tester",draft.getId(),true);
        assertEquals("CONFIRMED",draft.getStatus());verify(engine,times(1)).execute("tester",draft);verify(messages,times(2)).save(any());
    }
    @Test void cancelNeverWritesBusinessData(){
        service.decide("tester",draft.getId(),false);assertEquals("CANCELLED",draft.getStatus());verify(engine,never()).execute(any(),any());
    }
    @Test void staleSnapshotCannotOverwriteNewData(){
        when(engine.fingerprint(any(),any(),any())).thenReturn("changed");service.decide("tester",draft.getId(),true);
        assertEquals("STALE",draft.getStatus());verify(engine,never()).execute(any(),any());
    }
    @Test void removedFoodOrChangedVersionAlsoClosesTheStalePreview(){
        when(engine.fingerprint(any(),any(),any())).thenThrow(new IllegalStateException("Changed"));service.decide("tester",draft.getId(),true);
        assertEquals("STALE",draft.getStatus());verify(engine,never()).execute(any(),any());
    }
    @Test void expiredDraftCannotBeConfirmed(){draft.setExpiresAt(OffsetDateTime.now().minusSeconds(1));service.decide("tester",draft.getId(),true);assertEquals("EXPIRED",draft.getStatus());verify(engine,never()).execute(any(),any());}
    @Test void anotherUserCannotConfirmOrCancel(){var other=new User();other.setId(8L);when(access.lock("other")).thenReturn(other);assertThrows(SecurityException.class,()->service.decide("other",draft.getId(),true));verifyNoInteractions(engine);}
}
