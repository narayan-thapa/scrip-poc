package np.com.thapanarayan.backend.indicator.internal.studies;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import np.com.thapanarayan.backend.indicator.internal.BarSeriesAdapter;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

/** Unit tests for the four shipped studies over synthetic Ta4j series (no Spring, no Docker). */
class StudiesTest {

    private static CandleBar bar(LocalDate d, double o, double h, double l, double c, long v) {
        return new CandleBar(d, bd(o), bd(h), bd(l), bd(c), v);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    /**
     * A zig-zag series with clear pivots: up to 112, pullback to 104, rally to 124 (breaks the 112
     * swing high → bullish BOS), then a slide below 104 (bearish break). Enough for ATR/HMA warm-up.
     */
    private BarSeries trendThenReverse() {
        double[] path = {
                100, 102, 104, 106, 108, 110, 112,           // peak ~112 (idx 6)
                110, 108, 106, 104,                           // pullback trough ~104 (idx 10)
                106, 108, 110, 112, 114, 116, 118, 120, 122, 124, // rally breaks 112
                122, 118, 114, 110, 106, 102, 100, 98, 96     // slide breaks 104
        };
        List<CandleBar> bars = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        for (double p : path) {
            bars.add(bar(d, p, p + 0.5, p - 0.5, p, 1000));
            d = d.plusDays(1);
        }
        return BarSeriesAdapter.toSeries("TEST", bars);
    }

    @Test
    void hmaProducesAFiniteLine() {
        var result = new HullMovingAverageStudy().compute(trendThenReverse(), ParamValues.empty());
        assertThat(result).isInstanceOf(IndicatorResult.Lines.class);
        var hma = ((IndicatorResult.Lines) result).series().get("hma");
        assertThat(hma).isNotEmpty();
        assertThat(hma.get(hma.size() - 1).value()).isFinite();
    }

    @Test
    void utBotProducesStopLineAndFlips() {
        var result = new UtBotStudy().compute(trendThenReverse(), ParamValues.empty());
        assertThat(result).isInstanceOf(IndicatorResult.Signals.class);
        var sig = (IndicatorResult.Signals) result;
        assertThat(sig.plot()).isNotEmpty();
        // The up→down reversal should trigger at least one SELL event.
        assertThat(sig.events()).anyMatch(e -> e.side().equals("SELL"));
    }

    @Test
    void insideHammerDetectsCraftedPattern() {
        // Bar 0: wide range; Bar 1: inside + hammer (small body high, long lower shadow).
        List<CandleBar> bars = List.of(
                bar(LocalDate.of(2026, 1, 1), 100, 120, 80, 100, 1000),
                bar(LocalDate.of(2026, 1, 2), 110, 113, 95, 112, 1000));
        var result = new InsideHammerStudy().compute(BarSeriesAdapter.toSeries("T", bars), ParamValues.empty());
        var markers = ((IndicatorResult.Markers) result).markers();
        assertThat(markers).hasSize(1);
        assertThat(markers.get(0).text()).isEqualTo("IH");
    }

    @Test
    void smcProducesStructureLabels() {
        var result = new SmcStudy().compute(trendThenReverse(), ParamValues.empty());
        assertThat(result).isInstanceOf(IndicatorResult.Zones.class);
        var zones = (IndicatorResult.Zones) result;
        // A clean trend reversal yields at least one BOS/CHoCH label.
        assertThat(zones.labels()).isNotEmpty();
    }
}
