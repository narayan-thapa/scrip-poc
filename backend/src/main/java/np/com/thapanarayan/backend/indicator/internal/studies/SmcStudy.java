package np.com.thapanarayan.backend.indicator.internal.studies;

import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.indicator.api.CustomIndicator;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Box;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Label;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Ray;
import np.com.thapanarayan.backend.indicator.api.OutputKind;
import np.com.thapanarayan.backend.indicator.api.ParamSpec;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

/**
 * SMC — Smart Money Concepts (ZONE). A structural toolkit on fractal swing detection: market
 * structure breaks (BOS) and change-of-character (CHoCH) as labels/rays, fair-value-gap boxes
 * (3-bar imbalance), and order-block boxes (last opposing candle before a structure-breaking impulse).
 * Output: boxes/rays/labels rendered as chart primitives; also a composite bullish/bearish vote.
 */
@Component
public class SmcStudy implements CustomIndicator {

    @Override
    public IndicatorDescriptor descriptor() {
        return new IndicatorDescriptor("smc", "Smart Money Concepts", "Structure", OutputKind.ZONE,
                List.of(ParamSpec.intParam("swingLookback", 5, 2, 50),
                        ParamSpec.intParam("maxZones", 50, 5, 500)), true);
    }

    @Override
    public IndicatorResult compute(BarSeries series, ParamValues params) {
        int k = Math.max(2, params.getInt("swingLookback", 5));
        int maxZones = Math.max(5, params.getInt("maxZones", 50));

        List<Box> boxes = new ArrayList<>();
        List<Ray> rays = new ArrayList<>();
        List<Label> labels = new ArrayList<>();

        int begin = series.getBeginIndex();
        int end = series.getEndIndex();

        // Track the most recent confirmed swing high/low and the prevailing trend.
        double lastSwingHigh = Double.NaN;
        double lastSwingLow = Double.NaN;
        int trend = 0; // +1 up, -1 down

        for (int i = begin; i <= end; i++) {
            Bar bar = series.getBar(i);
            double close = bar.getClosePrice().doubleValue();

            // Confirm a swing centered at (i-k) once k future bars exist.
            int s = i - k;
            if (s - k >= begin) {
                if (isSwingHigh(series, s, k)) {
                    lastSwingHigh = series.getBar(s).getHighPrice().doubleValue();
                }
                if (isSwingLow(series, s, k)) {
                    lastSwingLow = series.getBar(s).getLowPrice().doubleValue();
                }
            }

            // Structure breaks.
            if (!Double.isNaN(lastSwingHigh) && close > lastSwingHigh) {
                String type = trend < 0 ? "CHoCH" : "BOS";
                labels.add(new Label(BarTimes.at(series, i), lastSwingHigh, type + "+"));
                rays.add(new Ray(BarTimes.at(series, i), lastSwingHigh, "#16a34a", type));
                addOrderBlock(series, i, true, boxes, maxZones);
                trend = 1;
                lastSwingHigh = Double.NaN;
            } else if (!Double.isNaN(lastSwingLow) && close < lastSwingLow) {
                String type = trend > 0 ? "CHoCH" : "BOS";
                labels.add(new Label(BarTimes.at(series, i), lastSwingLow, type + "-"));
                rays.add(new Ray(BarTimes.at(series, i), lastSwingLow, "#dc2626", type));
                addOrderBlock(series, i, false, boxes, maxZones);
                trend = -1;
                lastSwingLow = Double.NaN;
            }

            // Fair-value gaps (3-bar imbalance).
            if (i - 2 >= begin && boxes.size() < maxZones) {
                double highMinus2 = series.getBar(i - 2).getHighPrice().doubleValue();
                double lowMinus2 = series.getBar(i - 2).getLowPrice().doubleValue();
                double low = bar.getLowPrice().doubleValue();
                double high = bar.getHighPrice().doubleValue();
                if (low > highMinus2) { // bullish FVG
                    boxes.add(new Box(BarTimes.at(series, i - 2), BarTimes.at(series, i), low, highMinus2,
                            "rgba(22,163,74,0.15)", "FVG"));
                } else if (high < lowMinus2) { // bearish FVG
                    boxes.add(new Box(BarTimes.at(series, i - 2), BarTimes.at(series, i), lowMinus2, high,
                            "rgba(220,38,38,0.15)", "FVG"));
                }
            }
        }
        return new IndicatorResult.Zones(boxes, rays, labels);
    }

    private void addOrderBlock(BarSeries series, int breakIndex, boolean bullish, List<Box> boxes, int maxZones) {
        if (boxes.size() >= maxZones) {
            return;
        }
        // Last opposing candle in the 3 bars before the impulse.
        for (int j = breakIndex - 1; j >= Math.max(series.getBeginIndex(), breakIndex - 3); j--) {
            Bar b = series.getBar(j);
            boolean down = b.getClosePrice().isLessThan(b.getOpenPrice());
            if (bullish == down) { // bullish OB = last down candle; bearish OB = last up candle
                boxes.add(new Box(BarTimes.at(series, j), BarTimes.at(series, breakIndex),
                        b.getHighPrice().doubleValue(), b.getLowPrice().doubleValue(),
                        bullish ? "rgba(37,99,235,0.15)" : "rgba(217,119,6,0.15)", "OB"));
                return;
            }
        }
    }

    private boolean isSwingHigh(BarSeries series, int center, int k) {
        double h = series.getBar(center).getHighPrice().doubleValue();
        for (int j = center - k; j <= center + k; j++) {
            if (j != center && series.getBar(j).getHighPrice().doubleValue() >= h) {
                return false;
            }
        }
        return true;
    }

    private boolean isSwingLow(BarSeries series, int center, int k) {
        double l = series.getBar(center).getLowPrice().doubleValue();
        for (int j = center - k; j <= center + k; j++) {
            if (j != center && series.getBar(j).getLowPrice().doubleValue() <= l) {
                return false;
            }
        }
        return true;
    }
}
