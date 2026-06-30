package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;

/**
 * Published (AFTER_COMMIT) once a date's indicator snapshots are persisted, marking
 * completion of the indicator stage. The signal engine no longer consumes this — it
 * runs in parallel off {@code MarketDataReadyEvent} — so this currently has no
 * listener; it is retained as the stage's completion signal for observability and
 * future consumers.
 *
 * @param tradeDate             the date whose snapshots were computed
 * @param symbolsProcessed      number of symbols that got a snapshot
 * @param suppressNotifications carried through from {@code MarketDataReadyEvent} so a
 *                              historical backfill stays quiet end-to-end
 */
public record IndicatorsComputedEvent(
        LocalDate tradeDate,
        int symbolsProcessed,
        boolean suppressNotifications) {
}
