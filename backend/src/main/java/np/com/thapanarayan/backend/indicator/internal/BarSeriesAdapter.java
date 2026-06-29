package np.com.thapanarayan.backend.indicator.internal;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;

/**
 * Builds a Ta4j {@link BarSeries} from daily candles (§10.5). {@code DecimalNum}
 * is the canonical, BigDecimal-backed path used for signals/backtests;
 * {@code DoubleNum} is offered for chart overlays where speed beats exactness.
 *
 * <p>Each daily bar carries a one-day period and an end time of the trade date at
 * UTC midnight — the zone is immaterial for daily bars, and the parallel date
 * list in {@link IndicatorBarSeries} is what we map values back through.</p>
 */
final class BarSeriesAdapter {

    private static final Duration ONE_DAY = Duration.ofDays(1);

    private BarSeriesAdapter() {
    }

    static IndicatorBarSeries decimal(String symbol, List<DailyCandleView> candles) {
        return build(symbol, candles, DecimalNumFactory.getInstance());
    }

    static IndicatorBarSeries doublePrecision(String symbol, List<DailyCandleView> candles) {
        return build(symbol, candles, DoubleNumFactory.getInstance());
    }

    private static IndicatorBarSeries build(String symbol, List<DailyCandleView> candles, NumFactory factory) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withNumFactory(factory)
                .withName(symbol)
                .build();
        List<java.time.LocalDate> dates = new ArrayList<>(candles.size());
        for (DailyCandleView c : candles) {
            series.barBuilder()
                    .timePeriod(ONE_DAY)
                    .endTime(c.tradeDate().atStartOfDay().toInstant(ZoneOffset.UTC))
                    .openPrice(c.open())
                    .highPrice(c.high())
                    .lowPrice(c.low())
                    .closePrice(c.close())
                    .volume(c.volume())
                    .amount(c.turnover())
                    .bindTo(series)
                    .add();
            dates.add(c.tradeDate());
        }
        return new IndicatorBarSeries(series, dates);
    }
}
