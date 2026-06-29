package np.com.thapanarayan.backend.backtest.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point on the equity / drawdown curve (§7.3): mark-to-market portfolio equity
 * on a trading day and the running drawdown from the prior equity peak.
 *
 * @param drawdownPct drawdown from the running peak, percent (0 at a new high)
 */
public record EquityPointView(LocalDate date, BigDecimal equity, double drawdownPct) {
}
