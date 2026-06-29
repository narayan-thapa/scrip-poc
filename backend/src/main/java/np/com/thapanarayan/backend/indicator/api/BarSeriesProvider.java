package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;

import org.ta4j.core.BarSeries;

/**
 * Published adapter that turns a symbol's daily candle history into a Ta4j
 * {@link BarSeries}. The signal (Stage 5) and backtest (Stage 6) engines build
 * their own Ta4j {@code Indicator}/{@code Rule} graphs on top of the series this
 * returns, rather than re-implementing the candles→series mapping.
 *
 * <p>The series uses {@code DecimalNum} (BigDecimal-backed) — the canonical path
 * matching the {@code NUMERIC(18,4)} schema — for signal/backtest correctness.</p>
 */
public interface BarSeriesProvider {

    /**
     * Builds a daily {@link BarSeries} for {@code symbol} ending at {@code asOf},
     * including up to {@code lookback} trading days of warm-up history (ascending,
     * so the last bar is {@code asOf} when a candle exists for it).
     */
    BarSeries dailySeries(String symbol, LocalDate asOf, int lookback);
}
