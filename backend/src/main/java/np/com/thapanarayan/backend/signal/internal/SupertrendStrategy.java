package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S6 — Supertrend (§6, table). Close above the ATR(10,3) Supertrend band is an up
 * regime (bullish), below is a down regime (bearish); the distance from the band,
 * normalised by ATR, grades the conviction.
 */
@Component
class SupertrendStrategy extends Ta4jStrategy {

    private static final int ATR_LEN = 10;
    private static final double MULTIPLIER = 3.0;

    @Override
    public StrategyId id() {
        return StrategyId.S6;
    }

    @Override
    int minBars() {
        return ATR_LEN + 1;
    }

    @Override
    Indicator<Num> voteIndicator(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SuperTrendIndicator supertrend = new SuperTrendIndicator(series, ATR_LEN, MULTIPLIER);
        ATRIndicator atr = new ATRIndicator(series, ATR_LEN);
        return new FunctionalIndicator(series, ATR_LEN,
                i -> series.numFactory().numOf(state(close, supertrend, atr, i)));
    }

    private static double state(Indicator<Num> close, Indicator<Num> supertrend, Indicator<Num> atr, int i) {
        double c = SignalMath.safe(close, i);
        double st = SignalMath.safe(supertrend, i);
        double a = SignalMath.safe(atr, i);
        if (st == 0.0 || a <= 0.0) {
            return 0.0;
        }
        double distance = (c - st) / a; // positive when price is above the band
        return Math.signum(distance) * SignalMath.saturate(distance);
    }

    @Override
    List<Reason> describe(BarSeries series, int i, double g) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SuperTrendIndicator supertrend = new SuperTrendIndicator(series, ATR_LEN, MULTIPLIER);
        double c = SignalMath.safe(close, i);
        double st = SignalMath.safe(supertrend, i);
        boolean bullish = g > 0;

        String condition = bullish ? "Close above Supertrend band" : "Close below Supertrend band";
        String narrative = "Supertrend is in a %s regime: close %.2f is %s the band at %.2f."
                .formatted(bullish ? "bullish" : "bearish", c, bullish ? "above" : "below", st);

        return List.of(new Reason(id(), "Supertrend(10,3)", condition,
                "close=%.2f, supertrend=%.2f".formatted(c, st),
                bullish ? "close > band" : "close < band",
                SignalMath.round2(g), narrative));
    }
}
