package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Published whole-market breadth + totals for a date (from the NEPSE aggregate). */
public record MarketSummaryView(
        LocalDate tradeDate,
        int advances,
        int declines,
        int unchanged,
        long totalVolume,
        BigDecimal totalTurnover,
        long totalTrades) {
}
