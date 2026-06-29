package np.com.thapanarayan.backend.notification.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A persisted notification. {@code sent} is the dispatch outbox flag; {@code read} is UI state. */
@Entity
@Table(name = "notification")
class NotificationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "signal_id")
    private UUID signalId;

    @Column(name = "title", length = 160, nullable = false)
    private String title;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "sent", nullable = false)
    private boolean sent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationEntity() {
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getUserId() {
        return userId;
    }

    void setUserId(UUID userId) {
        this.userId = userId;
    }

    UUID getSignalId() {
        return signalId;
    }

    void setSignalId(UUID signalId) {
        this.signalId = signalId;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    String getBody() {
        return body;
    }

    void setBody(String body) {
        this.body = body;
    }

    boolean isRead() {
        return read;
    }

    void setRead(boolean read) {
        this.read = read;
    }

    boolean isSent() {
        return sent;
    }

    void setSent(boolean sent) {
        this.sent = sent;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
