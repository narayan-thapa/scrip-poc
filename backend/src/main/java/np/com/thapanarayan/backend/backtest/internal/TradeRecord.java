package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One closed (or marked-to-close) trade produced by the engine, before persistence. */
record TradeRecord(
        String symbol,
        LocalDate entryDate,
        BigDecimal entryPrice,
        LocalDate exitDate,
        BigDecimal exitPrice,
        long quantity,
        BigDecimal costs,
        BigDecimal pnl,
        double returnPct,
        String entryReason,
        String exitReason) {
}
