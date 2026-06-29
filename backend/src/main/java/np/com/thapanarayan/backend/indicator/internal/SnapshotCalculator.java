package np.com.thapanarayan.backend.indicator.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Bar;
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

/**
 * Computes the curated indicator snapshot (latest-bar values) for a {@link BarSeries}.
 * Pure and Spring-free so it is unit-testable. Values are read at the series end
 * index and rounded to the canonical 4-dp scale; an indicator that cannot yield a
 * value yet (warm-up / NaN) is recorded as {@code null} rather than failing.
 */
final class SnapshotCalculator {

    private static final int SCALE = 4;

    private SnapshotCalculator() {
    }

    /**
     * @param values flat catalog used for the JSONB column; the four promoted
     *               fields are duplicated here for the entity's promoted columns
     */
    record Result(
            int barCount,
            BigDecimal rsi14,
            BigDecimal ema9,
            BigDecimal ema21,
            BigDecimal atr14,
            List<IndicatorValue> values) {
    }

    static Result compute(BarSeries series) {
        int i = series.getEndIndex();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        BigDecimal rsi14 = val(new RSIIndicator(close, 14), i);
        BigDecimal ema9 = val(new EMAIndicator(close, 9), i);
        BigDecimal ema21 = val(new EMAIndicator(close, 21), i);
        BigDecimal ema50 = val(new EMAIndicator(close, 50), i);
        BigDecimal ema200 = val(new EMAIndicator(close, 200), i);
        BigDecimal sma20 = val(new SMAIndicator(close, 20), i);

        MACDIndicator macdInd = new MACDIndicator(close, 12, 26);
        BigDecimal macd = val(macdInd, i);
        BigDecimal macdSignal = val(new EMAIndicator(macdInd, 9), i);
        BigDecimal macdHist = macd != null && macdSignal != null
                ? macd.subtract(macdSignal).setScale(SCALE, RoundingMode.HALF_UP) : null;

        BollingerBandFacade bb = new BollingerBandFacade(series, 20, 2);
        BigDecimal bbUpper = val(bb.upper(), i);
        BigDecimal bbMiddle = val(bb.middle(), i);
        BigDecimal bbLower = val(bb.lower(), i);

        BigDecimal atr14 = val(new ATRIndicator(series, 14), i);
        BigDecimal adx14 = val(new ADXIndicator(series, 14), i);
        BigDecimal stochK = val(new StochasticOscillatorKIndicator(series, 14), i);
        BigDecimal cci20 = val(new CCIIndicator(series, 20), i);
        BigDecimal roc12 = val(new ROCIndicator(close, 12), i);
        BigDecimal willr14 = val(new WilliamsRIndicator(series, 14), i);
        BigDecimal obv = val(new OnBalanceVolumeIndicator(series), i);
        BigDecimal mfi14 = val(new MoneyFlowIndexIndicator(series, 14), i);
        BigDecimal supertrend = val(new SuperTrendIndicator(series, 10, 3.0), i);

        List<IndicatorValue> values = new ArrayList<>();
        add(values, "rsi14", rsi14);
        add(values, "ema9", ema9);
        add(values, "ema21", ema21);
        add(values, "ema50", ema50);
        add(values, "ema200", ema200);
        add(values, "sma20", sma20);
        add(values, "macd", macd);
        add(values, "macd_signal", macdSignal);
        add(values, "macd_hist", macdHist);
        add(values, "bb_upper", bbUpper);
        add(values, "bb_middle", bbMiddle);
        add(values, "bb_lower", bbLower);
        add(values, "atr14", atr14);
        add(values, "adx14", adx14);
        add(values, "stoch_k14", stochK);
        add(values, "cci20", cci20);
        add(values, "roc12", roc12);
        add(values, "willr14", willr14);
        add(values, "obv", obv);
        add(values, "mfi14", mfi14);
        add(values, "supertrend", supertrend);
        addPivots(values, series.getBar(i));

        return new Result(series.getBarCount(), rsi14, ema9, ema21, atr14, values);
    }

    /** Classic floor-trader pivots from the day's own High/Low/Close. */
    private static void addPivots(List<IndicatorValue> values, Bar bar) {
        BigDecimal high = bar.getHighPrice().bigDecimalValue();
        BigDecimal low = bar.getLowPrice().bigDecimalValue();
        BigDecimal closePrice = bar.getClosePrice().bigDecimalValue();
        BigDecimal pivot = high.add(low).add(closePrice).divide(BigDecimal.valueOf(3), SCALE, RoundingMode.HALF_UP);
        BigDecimal range = high.subtract(low);
        add(values, "pivot", scale(pivot));
        add(values, "r1", scale(pivot.multiply(BigDecimal.valueOf(2)).subtract(low)));
        add(values, "s1", scale(pivot.multiply(BigDecimal.valueOf(2)).subtract(high)));
        add(values, "r2", scale(pivot.add(range)));
        add(values, "s2", scale(pivot.subtract(range)));
    }

    private static void add(List<IndicatorValue> values, String key, BigDecimal value) {
        if (value != null) {
            values.add(new IndicatorValue(key, value));
        }
    }

    private static BigDecimal val(Indicator<Num> indicator, int index) {
        try {
            Num n = indicator.getValue(index);
            if (n == null) {
                return null;
            }
            return scale(n.bigDecimalValue());
        } catch (RuntimeException notAvailable) {
            return null; // NaN.bigDecimalValue() throws, and warm-up edge cases land here too
        }
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
