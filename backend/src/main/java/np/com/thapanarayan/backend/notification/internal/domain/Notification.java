package np.com.thapanarayan.backend.notification.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A persisted, deliverable notification. {@code sent} is the outbox flag; {@code readFlag} is user state. */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "signal_id")
    private UUID signalId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "read_flag", nullable = false)
    private boolean readFlag;

    @Column(nullable = false)
    private boolean sent;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    protected Notification() {
    }

    public Notification(UUID userId, UUID signalId, String title, String body) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.signalId = signalId;
        this.title = title;
        this.body = body;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSignalId() {
        return signalId;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isReadFlag() {
        return readFlag;
    }

    public void markRead() {
        this.readFlag = true;
    }

    public boolean isSent() {
        return sent;
    }

    public void markSent() {
        this.sent = true;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
