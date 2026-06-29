package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S1 — Trend following (§6, table). EMA(9) vs EMA(21) sets the direction; ADX(14)
 * gates and grades it (chop below ~20 is damped, a strong trend amplifies). The
 * graded state is {@code sign(ema9-ema21) * f(gap) * f(adx)}.
 */
@Component
class TrendFollowingStrategy extends Ta4jStrategy {

    private static final int FAST = 9;
    private static final int SLOW = 21;
    private static final int ADX_LEN = 14;
    private static final double ADX_TREND = 25.0;

    @Override
    public StrategyId id() {
        return StrategyId.S1;
    }

    @Override
    int minBars() {
        return SLOW + 1;
    }

    @Override
    Indicator<Num> voteIndicator(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema9 = new EMAIndicator(close, FAST);
        EMAIndicator ema21 = new EMAIndicator(close, SLOW);
        ADXIndicator adx = new ADXIndicator(series, ADX_LEN);
        return new FunctionalIndicator(series, SLOW, i -> series.numFactory().numOf(state(ema9, ema21, adx, i)));
    }

    private static double state(Indicator<Num> ema9, Indicator<Num> ema21, Indicator<Num> adx, int i) {
        double f = SignalMath.safe(ema9, i);
        double s = SignalMath.safe(ema21, i);
        if (s == 0.0) {
            return 0.0;
        }
        double gapRatio = (f - s) / s;
        double adxFactor = SignalMath.clamp(SignalMath.safe(adx, i) / ADX_TREND, 0.2, 1.0);
        double conf = SignalMath.saturate(gapRatio * 20.0) * adxFactor;
        return Math.signum(gapRatio) * conf;
    }

    @Override
    List<Reason> describe(BarSeries series, int i, double g) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator ema9 = new EMAIndicator(close, FAST);
        EMAIndicator ema21 = new EMAIndicator(close, SLOW);
        ADXIndicator adx = new ADXIndicator(series, ADX_LEN);
        double e9 = SignalMath.safe(ema9, i);
        double e21 = SignalMath.safe(ema21, i);
        double adxVal = SignalMath.safe(adx, i);
        boolean bullish = g > 0;

        boolean freshCross = i > 0
                && Math.signum(SignalMath.safe(ema9, i - 1) - SignalMath.safe(ema21, i - 1)) != Math.signum(e9 - e21);
        String condition = (bullish ? "EMA9 above EMA21" : "EMA9 below EMA21") + (freshCross ? " (fresh cross)" : "");
        String trend = adxVal >= ADX_TREND ? "ADX confirms a strong trend"
                : "ADX is muted, so the trend is weak";
        String narrative = "Short-term trend is %s: EMA9 (%.2f) is %s EMA21 (%.2f); %s (ADX %.1f)."
                .formatted(bullish ? "up" : "down", e9, bullish ? "above" : "below", e21, trend, adxVal);

        return List.of(new Reason(id(), "EMA(9/21)+ADX(14)", condition,
                "EMA9=%.2f, EMA21=%.2f, ADX=%.1f".formatted(e9, e21, adxVal),
                "EMA9 %s EMA21, ADX>%.0f".formatted(bullish ? ">" : "<", ADX_TREND),
                SignalMath.round2(g), narrative));
    }
}
