package np.com.thapanarayan.backend.indicator.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.WilliamsRIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.platform.api.DomainException;

/**
 * Resolves a catalog {@link IndicatorType} + integer params into live Ta4j
 * indicators, returning one entry per output line (ordered). Multi-line
 * indicators (MACD, Bollinger) yield several entries; everything else yields one.
 *
 * <p>This is the single place that knows each indicator's Ta4j constructor, so the
 * snapshot and ad-hoc series paths agree on how a given indicator is built.</p>
 */
final class IndicatorResolver {

    private IndicatorResolver() {
    }

    static Map<String, Indicator<Num>> resolve(IndicatorType type, BarSeries series, List<Integer> rawParams) {
        List<Integer> p = rawParams == null || rawParams.isEmpty() ? type.defaultParams() : rawParams;
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Map<String, Indicator<Num>> lines = new LinkedHashMap<>();
        switch (type) {
            case SMA -> lines.put("sma", new SMAIndicator(close, param(p, 0, 20)));
            case EMA -> lines.put("ema", new EMAIndicator(close, param(p, 0, 9)));
            case RSI -> lines.put("rsi", new RSIIndicator(close, param(p, 0, 14)));
            case MACD -> {
                MACDIndicator macd = new MACDIndicator(close, param(p, 0, 12), param(p, 1, 26));
                lines.put("macd", macd);
                lines.put("signal", new EMAIndicator(macd, param(p, 2, 9)));
            }
            case BBANDS -> {
                BollingerBandFacade bb = new BollingerBandFacade(series, param(p, 0, 20), param(p, 1, 2));
                lines.put("upper", bb.upper());
                lines.put("middle", bb.middle());
                lines.put("lower", bb.lower());
            }
            case ATR -> lines.put("atr", new ATRIndicator(series, param(p, 0, 14)));
            case ADX -> lines.put("adx", new ADXIndicator(series, param(p, 0, 14)));
            case SUPERTREND -> lines.put("supertrend",
                    new SuperTrendIndicator(series, param(p, 0, 10), (double) param(p, 1, 3)));
            case STOCH -> lines.put("k", new StochasticOscillatorKIndicator(series, param(p, 0, 14)));
            case CCI -> lines.put("cci", new CCIIndicator(series, param(p, 0, 20)));
            case ROC -> lines.put("roc", new ROCIndicator(close, param(p, 0, 12)));
            case WILLR -> lines.put("willr", new WilliamsRIndicator(series, param(p, 0, 14)));
            case OBV -> lines.put("obv", new OnBalanceVolumeIndicator(series));
            case MFI -> lines.put("mfi", new MoneyFlowIndexIndicator(series, param(p, 0, 14)));
        }
        return lines;
    }

    private static int param(List<Integer> params, int index, int fallback) {
        if (index >= params.size()) {
            return fallback;
        }
        Integer v = params.get(index);
        if (v == null || v < 1) {
            throw new DomainException("BAD_INDICATOR_PARAM",
                    "indicator parameter at position " + index + " must be a positive integer");
        }
        return v;
    }
}
