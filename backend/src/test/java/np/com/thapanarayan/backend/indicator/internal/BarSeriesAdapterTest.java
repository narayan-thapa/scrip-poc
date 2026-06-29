package np.com.thapanarayan.backend.indicator.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;

/** The adapter maps candles 1:1 into a Ta4j series, preserving order and close prices. */
class BarSeriesAdapterTest {

    static DailyCandleView candle(LocalDate date, double close, long volume) {
        BigDecimal c = BigDecimal.valueOf(close);
        return new DailyCandleView("ABC", date, c, c.add(BigDecimal.ONE), c.subtract(BigDecimal.ONE), c,
                volume, c.multiply(BigDecimal.valueOf(volume)), c, null, null, 1);
    }

    static List<DailyCandleView> fiveDays() {
        LocalDate d = LocalDate.of(2026, 6, 1);
        return List.of(
                candle(d, 10, 100),
                candle(d.plusDays(1), 11, 100),
                candle(d.plusDays(2), 12, 100),
                candle(d.plusDays(3), 13, 100),
                candle(d.plusDays(4), 14, 100));
    }

    @Test
    void buildsSeriesPreservingOrderAndCloses() {
        IndicatorBarSeries s = BarSeriesAdapter.decimal("ABC", fiveDays());

        assertThat(s.barCount()).isEqualTo(5);
        assertThat(s.dates()).containsExactly(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2), LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5));

        ClosePriceIndicator close = new ClosePriceIndicator(s.series());
        assertThat(close.getValue(s.endIndex()).doubleValue()).isEqualTo(14.0);
        assertThat(close.getValue(0).doubleValue()).isEqualTo(10.0);
    }
}
