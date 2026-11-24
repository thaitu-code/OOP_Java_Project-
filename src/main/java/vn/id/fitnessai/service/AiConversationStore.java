package vn.id.fitnessai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.id.fitnessai.entity.*;
import vn.id.fitnessai.repository.*;
import java.time.*;
import java.util.*;

/** Short, atomic DB transactions. The model is called only after these methods return. */
@Service @RequiredArgsConstructor
public class AiConversationStore {
    private final AiAccess access;
    private final AiChatTurnRepository turns;
    private final AiChatMessageRepository messages;
    private final AiActionDraftRepository drafts;
    private final AiDraftEngine engine;
    private final jakarta.persistence.EntityManager entityManager;
    private final tools.jackson.databind.ObjectMapper mapper;

    public record Ticket(UUID turnId, boolean generate) {}
    public record Message(Long id, String sessionId, UUID turnId, String sender, String content, String status, OffsetDateTime createdAt) {}
    public record Proposal(UUID id, String kind, String preview, String status, String result, Long recommendationId, OffsetDateTime expiresAt) {}
    public record Reply(UUID requestId, String status, Message userMessage, Message aiMessage, List<Proposal> proposals, String error, boolean retryable) {}
    private Message view(AiChatMessage m) { return new Message(m.getId(), m.getSessionId(), m.getTurnId(), m.getSender(), m.getContent(), m.getStatus(), m.getCreatedAt()); }
    public Proposal view(AiActionDraft d) { return new Proposal(d.getId(), d.getKind(), d.getPreview(), d.getStatus(), d.getResult(), d.getRecommendationId(), d.getExpiresAt()); }

    @Transactional
    public Ticket begin(String username, String session, String text, UUID requestId) {
        if (session == null || !session.matches("[A-Za-z0-9_-]{8,80}")) throw new IllegalArgumentException("Phiên chat không hợp lệ");
        if (text == null || text.isBlank() || text.length() > 3000 || requestId == null) throw new IllegalArgumentException("Tin nhắn cần 1-3000 ký tự và định danh yêu cầu");
        User user = access.lock(username);
        var existing = turns.findByUser_IdAndRequestId(user.getId(), requestId);
        if (existing.isPresent()) {
            var turn = existing.get();
            if (!session.equals(turn.getSessionId()) || !text.trim().equals(turn.getContent())) throw new IllegalArgumentException("Định danh yêu cầu đã được sử dụng cho nội dung khác");
            if (turn.getStatus().equals("FAILED") || turn.getStatus().equals("PENDING") && turn.getCreatedAt().isBefore(OffsetDateTime.now().minusSeconds(90))) {
                if (turn.getAttempts() >= 3) throw new IllegalArgumentException("Lượt này đã thử lại 3 lần; hãy gửi yêu cầu mới");
                turn.setStatus("PENDING"); turn.setError(null); turn.setCreatedAt(OffsetDateTime.now()); turn.setAttempts(turn.getAttempts() + 1);
                messages.findByUser_IdAndTurnIdOrderByIdAsc(user.getId(), turn.getId()).forEach(m -> m.setStatus("PENDING"));
                return new Ticket(turn.getId(), true);
            }
            return new Ticket(turn.getId(), false);
        }
        if (turns.existsByUser_IdAndStatusAndCreatedAtAfter(user.getId(), "PENDING", OffsetDateTime.now().minusSeconds(90)))
            throw new IllegalStateException("AI đang xử lý một yêu cầu của bạn. Hãy chờ kết quả trước khi gửi tiếp");
        if (turns.countByUser_IdAndCreatedAtAfter(user.getId(), OffsetDateTime.now().minusMinutes(1)) >= 8)
            throw new AiRateLimitException("Bạn gửi yêu cầu quá nhanh. Hãy đợi một phút");
        var turn = new AiChatTurn(); turn.setUser(user); turn.setSessionId(session); turn.setContent(text.trim()); turn.setRequestId(requestId);
        turns.save(turn);
        var m = new AiChatMessage(); m.setUser(user); m.setSessionId(session); m.setTurnId(turn.getId()); m.setSender("USER"); m.setContent(text.trim()); m.setStatus("PENDING"); messages.save(m);
        return new Ticket(turn.getId(), true);
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> history(String username, String session, UUID currentTurn) {
        User user = access.require(username);
        var recent = new ArrayList<>(messages.findByUser_IdAndSessionIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(user.getId(), session, Long.MAX_VALUE, PageRequest.of(0,200)));
        Collections.reverse(recent);
        Map<UUID,List<AiChatMessage>> pairs = new LinkedHashMap<>();
        for (var m : recent) if (m.getStatus().equals("COMPLETE") && !m.getTurnId().equals(currentTurn)) pairs.computeIfAbsent(m.getTurnId(), k -> new ArrayList<>()).add(m);
        var completed = pairs.values().stream().filter(p -> p.size() == 2 && p.get(0).getSender().equals("USER") && p.get(1).getSender().equals("AI")).toList();
        List<Map<String,Object>> prompt = new ArrayList<>();
        // Preserve whole turns, with a bounded provider payload; UI history is not truncated.
        int characters = 0;
        var selected = new ArrayDeque<List<AiChatMessage>>();
        for (int i = completed.size()-1; i >= 0; i--) {
            var pair = completed.get(i);
            int size = pair.stream().mapToInt(m -> m.getContent().length()).sum();
            if (characters + size > 100_000) break;
            selected.addFirst(pair); characters += size;
        }
        for (var pair : selected) for (var m : pair)
            prompt.add(Map.of("role", m.getSender().equals("USER") ? "user" : "model", "text", m.getContent()));
        return prompt;
    }

    @Transactional(readOnly = true)
    public Reply activeTurn(String username, String session) {
        var user = access.require(username);
        return turns.findFirstByUser_IdAndSessionIdOrderByCreatedAtDesc(user.getId(), session)
                .filter(t -> Set.of("PENDING", "FAILED").contains(t.getStatus()))
                .filter(t -> messages.findByUser_IdAndTurnIdOrderByIdAsc(user.getId(), t.getId()).stream()
                        .anyMatch(m -> m.getSender().equals("USER") && m.getDeletedAt() == null))
                .map(t -> reply(username, t.getRequestId())).orElse(null);
    }

    @Transactional
    public void finish(String username, UUID turnId, String answer, List<AiDraftEngine.Prepared> proposed, String expectedBase) {
        // Open-EntityManager-in-View must not reuse the pre-provider snapshots here.
        entityManager.clear();
        User user = access.lock(username);
        var turn = turns.findById(turnId).orElseThrow();
        if (!turn.getUser().getId().equals(user.getId()) || !turn.getStatus().equals("PENDING")) throw new IllegalStateException("Lượt chat không còn chờ kết quả");
        if (!proposed.isEmpty() && !engine.baseFingerprint(username).equals(expectedBase)) throw new IllegalStateException("Dữ liệu vừa thay đổi trong lúc AI xử lý. Hãy gửi lại để xem đề xuất mới");
        // Only an explicitly referenced draft is superseded; unrelated proposals remain independently reviewable.
        for (var p : proposed) {
            var replaces = mapper.readTree(p.payload()).path("supersedesDraftId").asText("");
            if (!replaces.isBlank()) {
                var old = drafts.findById(UUID.fromString(replaces)).orElseThrow(() -> new IllegalArgumentException("Đề xuất cần sửa không còn tồn tại"));
                if (!old.getUser().getId().equals(user.getId()) || !old.getSessionId().equals(turn.getSessionId()) || !old.getStatus().equals("PENDING"))
                    throw new IllegalArgumentException("Đề xuất cần sửa không còn chờ xác nhận");
                old.setStatus("SUPERSEDED");
            }
        }
        for (var p : proposed) {
            var d = new AiActionDraft(); d.setUser(user); d.setSessionId(turn.getSessionId()); d.setTurnId(turnId);
            d.setKind(p.kind()); d.setPayload(p.payload()); d.setPreview(p.preview()); d.setFingerprint(p.fingerprint()); drafts.save(d);
        }
        for (var m : messages.findByUser_IdAndTurnIdOrderByIdAsc(user.getId(), turnId)) m.setStatus("COMPLETE");
        var ai = new AiChatMessage(); ai.setUser(user); ai.setSessionId(turn.getSessionId()); ai.setTurnId(turnId); ai.setSender("AI"); ai.setContent(answer); messages.save(ai);
        turn.setStatus("COMPLETE");
    }

    @Transactional
    public void fail(String username, UUID turnId, String error) {
        User user = access.lock(username); var turn = turns.findById(turnId).orElseThrow();
        if (!turn.getUser().getId().equals(user.getId())) throw new SecurityException("Không có quyền");
        if (!turn.getStatus().equals("PENDING")) return;
        turn.setStatus("FAILED"); turn.setError(error);
        messages.findByUser_IdAndTurnIdOrderByIdAsc(user.getId(), turnId).forEach(m -> m.setStatus("FAILED"));
    }

    @Transactional(readOnly = true)
    public Reply reply(String username, UUID requestId) {
        User user = access.require(username);
        var turn = turns.findByUser_IdAndRequestId(user.getId(), requestId).orElseThrow(() -> new NoSuchElementException("Không tìm thấy lượt chat"));
        var list = messages.findByUser_IdAndTurnIdOrderByIdAsc(user.getId(), turn.getId());
        return new Reply(requestId, turn.getStatus(), list.stream().filter(m -> m.getSender().equals("USER")).map(this::view).findFirst().orElse(null),
                list.stream().filter(m -> m.getSender().equals("AI")).map(this::view).findFirst().orElse(null),
                drafts.findByUser_IdAndTurnIdOrderByCreatedAtAsc(user.getId(), turn.getId()).stream().map(this::view).toList(), turn.getError(), turn.getAttempts() < 3);
    }

    @Transactional
    public List<Proposal> pending(String username, String session) {
        var user = access.lock(username); var list = drafts.findByUser_IdAndSessionIdAndStatusOrderByCreatedAtAsc(user.getId(), session, "PENDING");
        for (var d : list) if (d.getExpiresAt().isBefore(OffsetDateTime.now())) d.setStatus("EXPIRED");
        return list.stream().filter(d -> d.getStatus().equals("PENDING")).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String,Object>> pendingContext(String username, String session) {
        var user = access.require(username);
        return drafts.findByUser_IdAndSessionIdAndStatusOrderByCreatedAtAsc(user.getId(), session, "PENDING").stream()
                .filter(d -> d.getExpiresAt().isAfter(OffsetDateTime.now()))
                .map(d -> Map.<String,Object>of("id", d.getId(), "kind", d.getKind(), "payload", mapper.readTree(d.getPayload()), "preview", d.getPreview())).toList();
    }
}
