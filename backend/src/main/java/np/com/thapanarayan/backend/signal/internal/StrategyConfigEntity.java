package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-strategy confluence configuration (§6.4). Seeded by Flyway and edited via the
 * {@code /strategies} API; the scorer reads enabled weights from here so tuning is
 * data, not a redeploy. The id is the {@code StrategyId} name (S1 .. S8).
 */
@Entity
@Table(name = "strategy_config")
class StrategyConfigEntity {

    @Id
    @Column(name = "strategy_id", length = 4, nullable = false)
    private String strategyId;

    @Column(name = "label", length = 64, nullable = false)
    private String label;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "weight", nullable = false)
    private BigDecimal weight;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StrategyConfigEntity() {
    }

    String getStrategyId() {
        return strategyId;
    }

    void setStrategyId(String strategyId) {
        this.strategyId = strategyId;
    }

    String getLabel() {
        return label;
    }

    void setLabel(String label) {
        this.label = label;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    BigDecimal getWeight() {
        return weight;
    }

    void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
