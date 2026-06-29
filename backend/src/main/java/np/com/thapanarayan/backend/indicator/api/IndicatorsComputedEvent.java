package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;

/**
 * Published (AFTER_COMMIT) once a date's indicator snapshots are persisted. Drives
 * the signal engine (Stage 5).
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
