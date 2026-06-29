package np.com.thapanarayan.backend.notification.internal;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.notification.api.NotificationView;

/**
 * Pushes realtime messages over both channels (§10.9): STOMP for connected WebSocket
 * clients and SSE for the fallback. Per-user notifications go to the user queue; the
 * "signals generated" heartbeat is broadcast to all subscribers of {@code /topic/signals}.
 */
@Component
class RealtimeGateway {

    private final SimpMessagingTemplate stomp;
    private final SseRegistry sse;

    RealtimeGateway(SimpMessagingTemplate stomp, SseRegistry sse) {
        this.stomp = stomp;
        this.sse = sse;
    }

    void sendNotification(UUID userId, NotificationView notification) {
        stomp.convertAndSendToUser(userId.toString(), "/queue/notifications", notification);
        sse.send(userId, "notification", notification);
    }

    void broadcastSignals(LocalDate tradeDate, int count) {
        stomp.convertAndSend("/topic/signals", new SignalsBroadcast(tradeDate, count));
    }

    record SignalsBroadcast(LocalDate tradeDate, int signalsGenerated) {
    }
}
