package np.com.thapanarayan.backend.notification.internal;

import np.com.thapanarayan.backend.notification.internal.domain.Notification;

/** Payload for the feed + SSE push. */
public record NotificationDto(String id, String signalId, String symbol, String title, String body,
                              boolean read, String createdAt) {

    static NotificationDto from(Notification n) {
        return new NotificationDto(n.getId().toString(),
                n.getSignalId() == null ? null : n.getSignalId().toString(),
                n.getSymbol(), n.getTitle(), n.getBody(), n.isReadFlag(), n.getCreatedAt().toString());
    }
}
