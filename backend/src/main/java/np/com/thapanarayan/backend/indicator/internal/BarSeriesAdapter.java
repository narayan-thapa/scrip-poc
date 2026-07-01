package np.com.thapanarayan.backend.indicator.internal;

import java.util.List;
import np.com.thapanarayan.backend.indicator.api.BarSeriesFactory;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import org.ta4j.core.BarSeries;

/** Internal convenience over the published {@link BarSeriesFactory}. */
public final class BarSeriesAdapter {

    private BarSeriesAdapter() {
    }

    public static BarSeries toSeries(String name, List<CandleBar> candles) {
        return BarSeriesFactory.fromCandles(name, candles);
    }
}
