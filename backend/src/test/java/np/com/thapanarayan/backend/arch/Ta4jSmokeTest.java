package np.com.thapanarayan.backend.arch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

/**
 * Ta4j smoke / regression test on Java 25.
 *
 * <p>Ta4j's bytecode targets Java 8 (so it loads on 25) but the maintainers do not yet declare
 * official Java-25 support. This pins the version and verifies the indicators we actually rely on
 * (SMA/EMA/RSI over a {@code DecimalNum} series) compute finite, correct values on this JVM. If a
 * future Ta4j or JDK bump breaks indicator math, this fails fast in CI.
 */
class Ta4jSmokeTest {

    private BarSeries series() {
        // DecimalNum = BigDecimal-backed, matching the NUMERIC(18,4) schema and the canonical
        // signal/backtest path (DoubleNum is only used for chart overlays).
        BarSeries series = new BaseBarSeriesBuilder()
                .withName("smoke")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();

        double[] closes = {
                10, 11, 12, 11, 13, 14, 13, 15, 16, 15,
                17, 18, 17, 19, 20, 19, 21, 22, 21, 23,
                24, 23, 25, 26, 25, 27, 28, 27, 29, 30
        };
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        for (double close : closes) {
            t = t.plus(Duration.ofDays(1));
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(t)
                    .openPrice(close)
                    .highPrice(close + 1)
                    .lowPrice(close - 1)
                    .closePrice(close)
                    .volume(1_000)
                    .add();
        }
        return series;
    }

    @Test
    void decimalNumFactoryProducesDecimalNum() {
        Num two = series().numFactory().numOf(2);
        // Canonical path must be BigDecimal-backed DecimalNum, not DoubleNum.
        assertThat(two).isInstanceOf(org.ta4j.core.num.DecimalNum.class);
        assertThat(two.plus(series().numFactory().numOf(3)).doubleValue()).isEqualTo(5.0);
    }

    @Test
    void smaEmaRsiComputeFiniteValuesAfterWarmup() {
        BarSeries series = series();
        int end = series.getEndIndex();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        Num sma = new SMAIndicator(close, 5).getValue(end);
        Num ema = new EMAIndicator(close, 5).getValue(end);
        Num rsi = new RSIIndicator(close, 14).getValue(end);

        assertThat(sma.isNaN()).as("SMA should be defined after warm-up").isFalse();
        assertThat(ema.isNaN()).as("EMA should be defined after warm-up").isFalse();
        assertThat(rsi.isNaN()).as("RSI should be defined after warm-up").isFalse();

        // Series is strongly up-trending → RSI well above 50.
        assertThat(rsi.doubleValue()).isBetween(50.0, 100.0);
        // SMA of the last 5 closes {27,28,27,29,30} = 28.2.
        assertThat(sma.doubleValue()).isCloseTo(28.2, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void earlyBarsAreNaNDuringWarmupWindow() {
        // Documents the gotcha the signal engine must guard: indicators return NaN before their
        // look-back window is satisfied; signals must be suppressed until then.
        BarSeries series = series();
        Num rsiAtStart = new RSIIndicator(new ClosePriceIndicator(series), 14).getValue(0);
        assertThat(rsiAtStart.isNaN()).isTrue();
    }
}
