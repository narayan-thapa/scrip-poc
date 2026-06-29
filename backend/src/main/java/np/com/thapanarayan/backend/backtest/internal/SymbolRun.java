package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NavigableMap;

/**
 * The per-symbol outcome of a backtest: its trades and the daily sub-account equity
 * curve. The service aggregates these into the shared-capital portfolio result.
 *
 * @param equityByDate cash + mark-to-market holdings on each in-range trading day
 * @param barsInPosition trading days the sub-account held a position (for exposure)
 * @param totalBars      total in-range trading days
 */
record SymbolRun(
        String symbol,
        List<TradeRecord> trades,
        NavigableMap<LocalDate, BigDecimal> equityByDate,
        int barsInPosition,
        int totalBars) {
}
