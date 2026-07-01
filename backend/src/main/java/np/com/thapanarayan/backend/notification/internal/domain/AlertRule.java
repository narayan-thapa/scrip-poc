package np.com.thapanarayan.backend.notification.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A user-defined alert rule. {@code type} + {@code params} (JSONB) drive matching in the evaluator. */
@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> params = Map.of();

    @Column(nullable = false)
    private boolean enabled = true;

    protected AlertRule() {
    }

    public AlertRule(UUID userId, String type, Map<String, Object> params) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.params = params == null ? Map.of() : params;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
