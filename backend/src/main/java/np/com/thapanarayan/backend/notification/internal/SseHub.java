package np.com.thapanarayan.backend.notification.internal;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Per-user Server-Sent-Events fan-out. The frontend subscribes with its bearer token (via
 * fetch-event-source, since EventSource can't set headers). This is the realtime channel; STOMP
 * WebSocket is the architecture's multi-client primary and a documented follow-on.
 */
@Component
class SseHub {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 min

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(e -> list.remove(emitter));
        return emitter;
    }

    void send(UUID userId, String event, Object payload) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (IOException | RuntimeException e) {
                list.remove(emitter);
            }
        }
    }
}
