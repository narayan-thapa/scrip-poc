package np.com.thapanarayan.backend.signal.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tunable confluence configuration for one strategy (§6.4). Weights are adjusted
 * via backtests and edited through the {@code /strategies} API, so they are data,
 * not code.
 *
 * @param strategyId the strategy this row configures (S1 .. S8)
 * @param label      human-readable name
 * @param enabled    whether the strategy participates in the blend
 * @param weight     relative confluence weight, {@code >= 0}
 * @param updatedAt  when the configuration was last changed
 */
public record StrategyConfigView(
        StrategyId strategyId,
        String label,
        boolean enabled,
        BigDecimal weight,
        Instant updatedAt) {
}
