package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;

/**
 * Published (AFTER_COMMIT) once a date's candles, volume profiles and broker flow
 * are persisted. Both downstream stages listen for this and run concurrently: the
 * indicator engine computes snapshots, and the signal engine generates signals
 * (which recompute indicators from the candle series, so they need only market data).
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
