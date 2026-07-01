package np.com.thapanarayan.backend.indicator.internal.studies;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.indicator.api.CustomIndicator;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Point;
import np.com.thapanarayan.backend.indicator.api.OutputKind;
import np.com.thapanarayan.backend.indicator.api.ParamSpec;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * HMA — Hull Moving Average (LINE). {@code HMA(n) = WMA(2·WMA(n/2) − WMA(n), round(√n))}, composed
 * from Ta4j {@link WMAIndicator}. Smoother + less laggy than EMA. Numeric → feeds the signal engine.
 */
@Component
public class HullMovingAverageStudy implements CustomIndicator {

    @Override
    public IndicatorDescriptor descriptor() {
        return new IndicatorDescriptor("hma", "Hull Moving Average", "Trend", OutputKind.LINE,
                List.of(ParamSpec.intParam("period", 21, 2, 400)), true);
    }

    @Override
    public IndicatorResult compute(BarSeries series, ParamValues params) {
        int n = Math.max(2, params.getInt("period", 21));
        var close = new ClosePriceIndicator(series);
        var wmaHalf = new WMAIndicator(close, Math.max(1, n / 2));
        var wmaFull = new WMAIndicator(close, n);
        var raw = NumericIndicator.of(wmaHalf).multipliedBy(2).minus(wmaFull);
        var hma = new WMAIndicator(raw, (int) Math.round(Math.sqrt(n)));

        List<Point> points = new ArrayList<>();
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num v = hma.getValue(i);
            if (!v.isNaN()) {
                points.add(new Point(BarTimes.at(series, i), v.doubleValue()));
            }
        }
        return new IndicatorResult.Lines(Map.of("hma", points));
    }
}
