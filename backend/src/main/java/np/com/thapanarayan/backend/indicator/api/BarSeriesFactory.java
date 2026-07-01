package np.com.thapanarayan.backend.indicator.api;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;

/**
 * Published builder turning daily candles into a Ta4j {@link BarSeries} on the {@code DecimalNum}
 * (BigDecimal-backed) canonical path. Shared by the indicator engine and the signal engine so both
 * evaluate identical series.
 */
public final class BarSeriesFactory {

    private BarSeriesFactory() {
    }

    public static BarSeries fromCandles(String name, List<CandleBar> candles) {
        BarSeries series = new BaseBarSeriesBuilder()
                .withName(name)
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        for (CandleBar c : candles) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(c.tradeDate().atTime(15, 0).toInstant(ZoneOffset.UTC))
                    .openPrice(c.open())
                    .highPrice(c.high())
                    .lowPrice(c.low())
                    .closePrice(c.close())
                    .volume(c.volume())
                    .add();
        }
        return series;
    }
}
