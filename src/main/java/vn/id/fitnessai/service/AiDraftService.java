package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.time.OffsetDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class AiDraftService {
    private final AiAccess access;
    private final AiActionDraftRepository drafts;
    private final AiChatMessageRepository messages;
    private final AiDraftEngine engine;
    private final AiConversationStore store;
    private final ObjectMapper mapper;
    private final jakarta.persistence.EntityManager entityManager;

    public record Result(AiConversationStore.Proposal proposal, List<AiConversationStore.Message> messages) {}

    @Transactional
    public Result decide(String username, UUID id, boolean confirm) { return decide(username, id, confirm, false); }

    @Transactional
    public Result decide(String username, UUID id, boolean confirm, boolean inChatTurn) {
        entityManager.clear();
        User user = access.lock(username);
        var draft = drafts.findById(id).orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề xuất"));
        if (!draft.getUser().getId().equals(user.getId())) throw new SecurityException("Đề xuất không thuộc bạn");
        if (!draft.getStatus().equals("PENDING")) return new Result(store.view(draft), List.of());
        if (draft.getExpiresAt().isBefore(OffsetDateTime.now())) {
            draft.setStatus("EXPIRED"); draft.setResult("Đề xuất hết hạn; hãy yêu cầu bản xem trước mới.");
        } else if (!confirm) {
            draft.setStatus("CANCELLED"); draft.setResult("Đã hủy đề xuất; dữ liệu nghiệp vụ chưa thay đổi.");
        } else if (!matches(username, draft)) {
            draft.setStatus("STALE"); draft.setResult("Dữ liệu đã thay đổi hoặc đã sang ngày mới. Hãy yêu cầu bản xem trước mới.");
        } else {
            var result = engine.execute(username, draft);
            draft.setStatus("CONFIRMED"); draft.setResult(result.message()); draft.setRecommendationId(result.recommendationId());
        }
        List<AiConversationStore.Message> saved = new ArrayList<>();
        if (!inChatTurn) {
            UUID turn = UUID.randomUUID();
            for (String sender : List.of("USER", "AI")) {
                var m = new AiChatMessage(); m.setUser(user); m.setSessionId(draft.getSessionId()); m.setTurnId(turn); m.setSender(sender);
                m.setContent(sender.equals("USER") ? (confirm ? "Xác nhận" : "Hủy") + " đề xuất: " + draft.getPreview() : draft.getResult());
                messages.save(m); saved.add(new AiConversationStore.Message(m.getId(), m.getSessionId(), m.getTurnId(), m.getSender(), m.getContent(), m.getStatus(), m.getCreatedAt()));
            }
        }
        return new Result(store.view(draft), saved);
    }

    private boolean matches(String username, AiActionDraft draft) {
        try { return engine.fingerprint(username, draft.getKind(), mapper.readTree(draft.getPayload())).equals(draft.getFingerprint()); }
        catch (IllegalArgumentException | IllegalStateException | NoSuchElementException e) { return false; }
    }
}
