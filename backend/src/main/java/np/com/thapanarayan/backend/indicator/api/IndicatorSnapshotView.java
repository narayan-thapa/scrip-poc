package np.com.thapanarayan.backend.indicator.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The canonical per-(symbol, trade_date) indicator snapshot. {@code barCount} is
 * the number of history bars the values were computed over, so consumers (the
 * signal engine) can decide whether a look-back window is warmed up.
 *
 * @param values the full computed catalog as flat key/value pairs
 */
public record IndicatorSnapshotView(
        String symbol,
        LocalDate tradeDate,
        int barCount,
        BigDecimal rsi14,
        BigDecimal ema9,
        BigDecimal ema21,
        BigDecimal atr14,
        List<IndicatorValueView> values) {
}
