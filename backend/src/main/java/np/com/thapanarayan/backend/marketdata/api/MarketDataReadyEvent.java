package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;

/**
 * Published after a date's market data (candles / volume profile / broker flow / NEPSE aggregate)
 * is computed. The indicator engine (Phase 4) listens to this to compute its catalog.
 */
public record MarketDataReadyEvent(LocalDate tradeDate, boolean suppressNotifications) {
}
