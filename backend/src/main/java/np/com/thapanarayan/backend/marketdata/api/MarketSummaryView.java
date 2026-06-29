package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Market breadth and totals for a trade date, computed across every symbol with a
 * daily candle. Symbols whose change cannot be computed (no prior close) count as
 * {@code unchanged} for breadth purposes.
 */
public record MarketSummaryView(
        LocalDate tradeDate,
        int symbolCount,
        int advances,
        int declines,
        int unchanged,
        long totalVolume,
        BigDecimal totalTurnover,
        long totalTrades) {
}
