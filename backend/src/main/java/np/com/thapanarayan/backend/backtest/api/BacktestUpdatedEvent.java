package np.com.thapanarayan.backend.backtest.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Published (AFTER_COMMIT) when a backtest run finishes — including the automatic
 * refresh triggered by {@code SignalsGeneratedEvent} over the day's BUY list (§5).
 * Lets the UI / notifications surface fresh strategy validation alongside new signals.
 *
 * @param runId     the completed run
 * @param tradeDate the signal date that triggered the refresh (or the run's end date for a manual run)
 * @param symbols   the symbols covered by the run
 */
public record BacktestUpdatedEvent(UUID runId, LocalDate tradeDate, List<String> symbols) {
}
