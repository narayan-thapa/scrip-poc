package np.com.thapanarayan.backend.indicator.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;

/** Resolver wiring + curated snapshot. */
class IndicatorComputeTest {

    private static IndicatorBarSeries series() {
        List<DailyCandleView> candles = BarSeriesAdapterTest.fiveDays();
        return BarSeriesAdapter.decimal("ABC", candles);
    }

    /** A deterministic 60-bar walk with both up and down days, so warm-up windows
     *  (EMA/SMA/MACD up to ~50) are satisfied and RSI is well-defined. */
    private static IndicatorBarSeries longSeries() {
        List<DailyCandleView> candles = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        double close = 100;
        for (int i = 0; i < 60; i++) {
            close += ((i * 7) % 5) - 2; // cycles through negatives and positives
            if (close < 10) {
                close = 10 + (i % 3);
            }
            candles.add(BarSeriesAdapterTest.candle(d.plusDays(i), close, 100 + i));
        }
        return BarSeriesAdapter.decimal("ABC", candles);
    }

    @Test
    void smaResolvesToExactMovingAverage() {
        IndicatorBarSeries s = series();
        Map<String, Indicator<Num>> lines = IndicatorResolver.resolve(IndicatorType.SMA, s.series(), List.of(3));

        // SMA(3) over the last three closes 12,13,14 = 13.
        double sma = lines.get("sma").getValue(s.endIndex()).doubleValue();
        assertThat(sma).isCloseTo(13.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void multiLineIndicatorsExposeAllLines() {
        IndicatorBarSeries s = series();
        assertThat(IndicatorResolver.resolve(IndicatorType.MACD, s.series(), List.of()))
                .containsKeys("macd", "signal");
        assertThat(IndicatorResolver.resolve(IndicatorType.BBANDS, s.series(), List.of()))
                .containsKeys("upper", "middle", "lower");
    }

    @Test
    void snapshotComputesPromotedAndCatalogValues() {
        SnapshotCalculator.Result r = SnapshotCalculator.compute(longSeries().series());

        assertThat(r.barCount()).isEqualTo(60);
        // With 60 bars these warm-up windows are satisfied, so the values are present.
        assertThat(r.ema9()).isNotNull();
        assertThat(r.ema21()).isNotNull();
        assertThat(r.atr14()).isNotNull();
        assertThat(r.rsi14()).isNotNull();
        assertThat(r.rsi14().doubleValue()).isBetween(0.0, 100.0);
        // The JSONB catalog carries the warmed-up values plus classic pivots. ema200
        // is intentionally absent (needs ~200 bars), proving NaN warm-up values are
        // dropped rather than persisted as bogus numbers.
        assertThat(r.values()).extracting(IndicatorValue::key)
                .contains("ema9", "ema21", "ema50", "sma20", "macd", "macd_signal", "bb_middle",
                        "atr14", "rsi14", "pivot", "r1", "s1")
                .doesNotContain("ema200");
    }

    @Test
    void warmupValuesAreNaNFilteredOnShortSeries() {
        // 5 bars: EMA-family indicators are NaN in their warm-up window and dropped
        // (SMA, by contrast, averages whatever bars exist, so it stays defined).
        SnapshotCalculator.Result r = SnapshotCalculator.compute(series().series());
        assertThat(r.barCount()).isEqualTo(5);
        assertThat(r.ema21()).isNull();
        assertThat(r.values()).extracting(IndicatorValue::key)
                .contains("pivot") // pivots come straight from the bar, always present
                .doesNotContain("ema9", "ema21", "ema200");
    }

    @Test
    void unknownIndicatorKeyIsEmpty() {
        assertThat(IndicatorType.fromKey("nope")).isEmpty();
        assertThat(IndicatorType.fromKey("RSI")).contains(IndicatorType.RSI);
    }
}
