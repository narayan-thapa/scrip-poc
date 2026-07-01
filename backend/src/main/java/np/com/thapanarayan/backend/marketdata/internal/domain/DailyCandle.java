package np.com.thapanarayan.backend.marketdata.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Per-(symbol, date) OHLC + volume/turnover/VWAP. {@code prevClose}/{@code changePct} are null on first session. */
public record DailyCandle(
        String symbol,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume,
        BigDecimal turnover,
        int tradesCount,
        BigDecimal vwap,
        BigDecimal prevClose,
        BigDecimal changePct) {
}
