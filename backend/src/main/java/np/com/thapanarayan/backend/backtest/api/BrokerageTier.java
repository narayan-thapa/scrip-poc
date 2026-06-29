package np.com.thapanarayan.backend.backtest.api;

import java.math.BigDecimal;

/**
 * One tier of the tiered broker commission (§7.2): trades whose per-side transaction
 * value is at or below {@code uptoAmount} pay {@code rate} (a fraction, e.g. 0.0036
 * for 0.36%). A {@code null} {@code uptoAmount} is the open-ended top tier.
 */
public record BrokerageTier(BigDecimal uptoAmount, BigDecimal rate) {
}
