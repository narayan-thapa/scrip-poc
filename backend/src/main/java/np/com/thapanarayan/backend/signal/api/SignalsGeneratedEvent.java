package np.com.thapanarayan.backend.signal.api;

import java.time.LocalDate;

/**
 * Published (after commit) once the day's signals are generated. The fan-out point for Phases 6–8:
 * backtest refresh for the BUY list and notification matching both listen to this.
 */
public record SignalsGeneratedEvent(LocalDate tradeDate, int buyCount, int sellCount, boolean suppressNotifications) {
}
