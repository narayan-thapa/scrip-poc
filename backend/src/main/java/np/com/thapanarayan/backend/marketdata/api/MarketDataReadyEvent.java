package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;

/**
 * Published (AFTER_COMMIT) once a date's candles, volume profiles and broker flow
 * are persisted. The next pipeline stage (indicator engine) listens for this.
 *
 * @param tradeDate             the aggregated trade date
 * @param symbolsAggregated     number of symbols that produced market data
 * @param suppressNotifications carried through from {@code TradesIngestedEvent} so a
 *                              historical backfill stays quiet end-to-end
 */
public record MarketDataReadyEvent(
        LocalDate tradeDate,
        int symbolsAggregated,
        boolean suppressNotifications) {
}
