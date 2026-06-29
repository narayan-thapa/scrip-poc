package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Daily OHLCV read model, published to other modules and the API. The indicator
 * engine (Stage 4) consumes a series of these to build its Ta4j {@code BarSeries}.
 *
 * @param vwap           volume-weighted average price (turnover / volume)
 * @param previousClose  prior trading day's close, or {@code null} for the first
 *                       observed day
 * @param changePercent  percentage change vs {@code previousClose}, or {@code null}
 */
public record DailyCandleView(
        String symbol,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal turnover,
        BigDecimal vwap,
        BigDecimal previousClose,
        BigDecimal changePercent,
        int tradeCount) {
}
