package np.com.thapanarayan.backend.notification.api;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a user's notification feed (§10.9).
 *
 * @param signalId the signal that triggered it, or {@code null} for system messages
 */
public record NotificationView(
        UUID id,
        UUID signalId,
        String title,
        String body,
        boolean read,
        Instant createdAt) {
}
