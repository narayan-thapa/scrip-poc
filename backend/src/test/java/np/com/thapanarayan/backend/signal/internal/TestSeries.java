package np.com.thapanarayan.backend.signal.internal;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

/** Builds deterministic {@link BarSeries} fixtures for the strategy/scorer tests. */
final class TestSeries {

    private static final Duration ONE_DAY = Duration.ofDays(1);
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    private TestSeries() {
    }

    /** A series with the given close prices; each bar's H/L straddle the close, volume fixed. */
    static BarSeries of(double... closes) {
        return build(closes, constantVolume(closes.length));
    }

    static BarSeries of(double[] closes, long[] volumes) {
        return build(closes, volumes);
    }

    /** {@code n} bars rising by {@code step} from {@code start} — a clean uptrend. */
    static BarSeries uptrend(int n, double start, double step) {
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) {
            closes[i] = start + i * step;
        }
        return of(closes);
    }

    /** {@code n} bars falling by {@code step} from {@code start} — a clean downtrend. */
    static BarSeries downtrend(int n, double start, double step) {
        return uptrend(n, start, -step);
    }

    private static long[] constantVolume(int n) {
        long[] v = new long[n];
        java.util.Arrays.fill(v, 1_000L);
        return v;
    }

    private static BarSeries build(double[] closes, long[] volumes) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance())
                .withName("TEST")
                .build();
        for (int i = 0; i < closes.length; i++) {
            double c = closes[i];
            double prev = i == 0 ? c : closes[i - 1];
            double high = Math.max(c, prev) + 1.0;
            double low = Math.min(c, prev) - 1.0;
            series.barBuilder()
                    .timePeriod(ONE_DAY)
                    .endTime(START.plusDays(i).atStartOfDay().toInstant(ZoneOffset.UTC))
                    .openPrice(prev)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(c)
                    .volume(volumes[i])
                    .amount(c * volumes[i])
                    .bindTo(series)
                    .add();
        }
        return series;
    }
}
