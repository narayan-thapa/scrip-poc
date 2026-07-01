package np.com.thapanarayan.backend.signal.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** Per-strategy confluence weight + enabled flag (tunable via backtests in Phase 6). */
@Entity
@Table(name = "strategy_config")
public class StrategyConfig {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String type;

    @Column(nullable = false)
    private BigDecimal weight = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean enabled = true;

    protected StrategyConfig() {
    }

    public StrategyConfig(String id, String name, String type, BigDecimal weight, boolean enabled) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.weight = weight;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
