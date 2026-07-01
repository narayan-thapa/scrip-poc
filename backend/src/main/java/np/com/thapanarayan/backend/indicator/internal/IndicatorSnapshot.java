package np.com.thapanarayan.backend.indicator.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/** Canonical per-(symbol, date) indicator values: a JSONB map plus promoted columns. */
public record IndicatorSnapshot(
        String symbol,
        LocalDate tradeDate,
        Map<String, Double> values,
        BigDecimal rsi14,
        BigDecimal ema9,
        BigDecimal ema21,
        BigDecimal atr14) {
}
