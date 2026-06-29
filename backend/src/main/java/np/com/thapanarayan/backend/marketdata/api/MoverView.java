package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;

/** A single entry in a movers list (top gainers / losers / most-traded). */
public record MoverView(
        String symbol,
        BigDecimal close,
        BigDecimal previousClose,
        BigDecimal changePercent,
        long volume,
        BigDecimal turnover) {
}
