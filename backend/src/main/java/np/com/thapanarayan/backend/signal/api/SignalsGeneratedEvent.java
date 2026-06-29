package np.com.thapanarayan.backend.signal.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Published (AFTER_COMMIT) once a date's signals are persisted — the first
 * end-to-end milestone's terminal event (§5). Drives Stage 6 (backtest refresh on
 * the BUY list) and Stage 9 (alert evaluation / realtime push).
 *
 * @param tradeDate             the date signals were generated for
 * @param signalsCreated        number of (symbol) signals persisted
 * @param buySymbols            symbols whose action is {@link SignalAction#BUY}, for the backtest refresh
 * @param suppressNotifications carried through from upstream so a historical backfill stays quiet end-to-end
 */
public record SignalsGeneratedEvent(
        LocalDate tradeDate,
        int signalsCreated,
        List<String> buySymbols,
        boolean suppressNotifications) {
}
