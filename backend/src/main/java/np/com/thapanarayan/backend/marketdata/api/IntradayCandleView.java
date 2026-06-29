package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One intraday OHLCV bucket; {@code bucketStart} is the inclusive lower time edge. */
public record IntradayCandleView(
        String symbol,
        LocalDateTime bucketStart,
        int intervalMinutes,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal turnover,
        int tradeCount) {
}
