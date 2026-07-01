package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Published OHLCV bar for one (symbol, date) — the input the indicator engine adapts to Ta4j. */
public record CandleBar(
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
