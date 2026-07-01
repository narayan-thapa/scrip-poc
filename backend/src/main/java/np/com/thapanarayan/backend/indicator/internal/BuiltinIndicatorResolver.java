package np.com.thapanarayan.backend.indicator.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Point;
import np.com.thapanarayan.backend.indicator.api.OutputKind;
import np.com.thapanarayan.backend.indicator.api.ParamSpec;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.averages.WMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * The library-grade built-in catalog backed by Ta4j (§F4). Each id resolves to one or more Ta4j
 * {@code Indicator<Num>}s and is returned as typed {@link IndicatorResult.Lines}. NaN warm-up values
 * are dropped so the chart never shows garbage before an indicator's look-back window is satisfied.
 */
@Component
public class BuiltinIndicatorResolver {

    public List<IndicatorDescriptor> descriptors() {
        List<IndicatorDescriptor> d = new ArrayList<>();
        d.add(line("sma", "Simple Moving Average", "Trend", ParamSpec.intParam("period", 20, 2, 400)));
        d.add(line("ema", "Exponential Moving Average", "Trend", ParamSpec.intParam("period", 9, 2, 400)));
        d.add(line("wma", "Weighted Moving Average", "Trend", ParamSpec.intParam("period", 20, 2, 400)));
        d.add(line("rsi", "Relative Strength Index", "Momentum", ParamSpec.intParam("period", 14, 2, 200)));
        d.add(new IndicatorDescriptor("macd", "MACD", "Momentum", OutputKind.BAND,
                List.of(ParamSpec.intParam("shortPeriod", 12, 2, 200),
                        ParamSpec.intParam("longPeriod", 26, 2, 400),
                        ParamSpec.intParam("signalPeriod", 9, 2, 200)), true));
        d.add(new IndicatorDescriptor("bbands", "Bollinger Bands", "Volatility", OutputKind.BAND,
                List.of(ParamSpec.intParam("period", 20, 2, 400), ParamSpec.doubleParam("k", 2.0, 0.5, 5.0)), true));
        d.add(line("atr", "Average True Range", "Volatility", ParamSpec.intParam("period", 14, 2, 200)));
        d.add(line("adx", "Average Directional Index", "Trend", ParamSpec.intParam("period", 14, 2, 200)));
        d.add(line("mfi", "Money Flow Index", "Volume", ParamSpec.intParam("period", 14, 2, 200)));
        return d;
    }

    public boolean supports(String id) {
        return descriptors().stream().anyMatch(x -> x.id().equals(id));
    }

    public IndicatorResult compute(String id, BarSeries series, ParamValues p) {
        var close = new ClosePriceIndicator(series);
        return switch (id) {
            case "sma" -> single(series, new SMAIndicator(close, p.getInt("period", 20)), "sma");
            case "ema" -> single(series, new EMAIndicator(close, p.getInt("period", 9)), "ema");
            case "wma" -> single(series, new WMAIndicator(close, p.getInt("period", 20)), "wma");
            case "rsi" -> single(series, new RSIIndicator(close, p.getInt("period", 14)), "rsi");
            case "atr" -> single(series, new ATRIndicator(series, p.getInt("period", 14)), "atr");
            case "adx" -> single(series, new ADXIndicator(series, p.getInt("period", 14)), "adx");
            case "mfi" -> single(series, new MoneyFlowIndexIndicator(series, p.getInt("period", 14)), "mfi");
            case "macd" -> macd(series, close, p);
            case "bbands" -> bbands(series, close, p);
            default -> throw ApiException.notFound("Unknown built-in indicator: " + id);
        };
    }

    private IndicatorResult macd(BarSeries series, ClosePriceIndicator close, ParamValues p) {
        MACDIndicator macd = new MACDIndicator(close, p.getInt("shortPeriod", 12), p.getInt("longPeriod", 26));
        Indicator<Num> signal = macd.getSignalLine(p.getInt("signalPeriod", 9));
        Indicator<Num> histogram = NumericIndicator.of(macd).minus(signal);
        Map<String, List<Point>> lines = new LinkedHashMap<>();
        lines.put("macd", points(series, macd));
        lines.put("signal", points(series, signal));
        lines.put("histogram", points(series, histogram));
        return new IndicatorResult.Lines(lines);
    }

    private IndicatorResult bbands(BarSeries series, ClosePriceIndicator close, ParamValues p) {
        int period = p.getInt("period", 20);
        double k = p.getDouble("k", 2.0);
        var middle = new BollingerBandsMiddleIndicator(new SMAIndicator(close, period));
        var sd = new StandardDeviationIndicator(close, period);
        var kNum = series.numFactory().numOf(k);
        var upper = new BollingerBandsUpperIndicator(middle, sd, kNum);
        var lower = new BollingerBandsLowerIndicator(middle, sd, kNum);
        Map<String, List<Point>> lines = new LinkedHashMap<>();
        lines.put("upper", points(series, upper));
        lines.put("middle", points(series, middle));
        lines.put("lower", points(series, lower));
        return new IndicatorResult.Lines(lines);
    }

    private IndicatorResult single(BarSeries series, Indicator<Num> indicator, String name) {
        return new IndicatorResult.Lines(Map.of(name, points(series, indicator)));
    }

    private List<Point> points(BarSeries series, Indicator<Num> indicator) {
        List<Point> out = new ArrayList<>();
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num v = indicator.getValue(i);
            if (!v.isNaN()) {
                out.add(new Point(SeriesTimes.at(series, i), v.doubleValue()));
            }
        }
        return out;
    }

    private static IndicatorDescriptor line(String id, String name, String category, ParamSpec param) {
        return new IndicatorDescriptor(id, name, category, OutputKind.LINE, List.of(param), true);
    }
}
