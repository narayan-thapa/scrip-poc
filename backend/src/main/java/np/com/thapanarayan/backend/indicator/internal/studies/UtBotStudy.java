package np.com.thapanarayan.backend.indicator.internal.studies;

import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.indicator.api.CustomIndicator;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Event;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Point;
import np.com.thapanarayan.backend.indicator.api.OutputKind;
import np.com.thapanarayan.backend.indicator.api.ParamSpec;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;

/**
 * UT Bot (SIGNAL) — an ATR trailing stop that ratchets with price and flips on a close-through.
 * Buy when close crosses above the stop, sell when below. Output: the stop line + buy/sell events.
 */
@Component
public class UtBotStudy implements CustomIndicator {

    @Override
    public IndicatorDescriptor descriptor() {
        return new IndicatorDescriptor("utbot", "UT Bot", "Trend", OutputKind.SIGNAL,
                List.of(ParamSpec.doubleParam("keyValue", 1.0, 0.1, 10.0),
                        ParamSpec.intParam("atrPeriod", 10, 1, 100)), true);
    }

    @Override
    public IndicatorResult compute(BarSeries series, ParamValues params) {
        double a = params.getDouble("keyValue", 1.0);
        int c = Math.max(1, params.getInt("atrPeriod", 10));
        ATRIndicator atr = new ATRIndicator(series, c);

        List<Point> plot = new ArrayList<>();
        List<Event> events = new ArrayList<>();

        double stop = Double.NaN;
        double prevClose = Double.NaN;
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            double close = series.getBar(i).getClosePrice().doubleValue();
            double atrVal = atr.getValue(i).doubleValue();
            if (Double.isNaN(atrVal)) {
                prevClose = close;
                continue; // warm-up
            }
            double nLoss = a * atrVal;
            double newStop;
            if (Double.isNaN(stop)) {
                newStop = close - nLoss;
            } else if (close > stop && prevClose > stop) {
                newStop = Math.max(stop, close - nLoss);
            } else if (close < stop && prevClose < stop) {
                newStop = Math.min(stop, close + nLoss);
            } else if (close > stop) {
                newStop = close - nLoss;
            } else {
                newStop = close + nLoss;
            }

            String time = BarTimes.at(series, i);
            if (!Double.isNaN(stop)) {
                if (prevClose <= stop && close > newStop) {
                    events.add(new Event(time, "BUY", close));
                } else if (prevClose >= stop && close < newStop) {
                    events.add(new Event(time, "SELL", close));
                }
            }
            plot.add(new Point(time, newStop));
            stop = newStop;
            prevClose = close;
        }
        return new IndicatorResult.Signals(plot, events);
    }
}
