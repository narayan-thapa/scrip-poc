package np.com.thapanarayan.backend.indicator.internal.studies;

import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.indicator.api.CustomIndicator;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Marker;
import np.com.thapanarayan.backend.indicator.api.OutputKind;
import np.com.thapanarayan.backend.indicator.api.ParamSpec;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * Inside Hammer (MARKER): an inside bar ({@code high<prevHigh ∧ low>prevLow}) that is also a hammer
 * (small real body in the upper third, lower shadow ≥ shadowRatio×body, minimal upper shadow).
 * Bullish-reversal pattern. Output: a marker at each qualifying bar.
 */
@Component
public class InsideHammerStudy implements CustomIndicator {

    @Override
    public IndicatorDescriptor descriptor() {
        return new IndicatorDescriptor("inside-hammer", "Inside Hammer", "Pattern", OutputKind.MARKER,
                List.of(ParamSpec.doubleParam("bodyMaxPct", 0.33, 0.05, 0.9),
                        ParamSpec.doubleParam("shadowRatio", 2.0, 1.0, 5.0)), true);
    }

    @Override
    public IndicatorResult compute(BarSeries series, ParamValues params) {
        double bodyMaxPct = params.getDouble("bodyMaxPct", 0.33);
        double shadowRatio = params.getDouble("shadowRatio", 2.0);

        List<Marker> markers = new ArrayList<>();
        for (int i = series.getBeginIndex() + 1; i <= series.getEndIndex(); i++) {
            Bar bar = series.getBar(i);
            Bar prev = series.getBar(i - 1);
            double high = bar.getHighPrice().doubleValue();
            double low = bar.getLowPrice().doubleValue();
            double open = bar.getOpenPrice().doubleValue();
            double close = bar.getClosePrice().doubleValue();

            boolean inside = high < prev.getHighPrice().doubleValue() && low > prev.getLowPrice().doubleValue();
            if (!inside) {
                continue;
            }
            double range = high - low;
            if (range <= 0) {
                continue;
            }
            double body = Math.abs(close - open);
            double bodyTop = Math.max(close, open);
            double bodyBottom = Math.min(close, open);
            double upperShadow = high - bodyTop;
            double lowerShadow = bodyBottom - low;

            boolean smallBody = body <= bodyMaxPct * range;
            boolean longLowerShadow = lowerShadow >= shadowRatio * Math.max(body, range * 0.01);
            boolean smallUpperShadow = upperShadow <= body;
            boolean bodyInUpperThird = bodyBottom >= low + range * 0.5;

            if (smallBody && longLowerShadow && smallUpperShadow && bodyInUpperThird) {
                markers.add(new Marker(BarTimes.at(series, i), "belowBar", "arrowUp", "#16a34a", "IH"));
            }
        }
        return new IndicatorResult.Markers(markers);
    }
}
