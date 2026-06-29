package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S7 — VWAP / money flow (§6, table). Price above the rolling VWAP(14) signals
 * buyers in control (bullish), below signals sellers; MFI(14) corroborates with the
 * money-flow bias around its 50 mid-line. The two are blended 60/40.
 */
@Component
class VwapMoneyFlowStrategy extends Ta4jStrategy {

    private static final int VWAP_LEN = 14;
    private static final int MFI_LEN = 14;

    @Override
    public StrategyId id() {
        return StrategyId.S7;
    }

    @Override
    int minBars() {
        return VWAP_LEN + 1;
    }

    @Override
    Indicator<Num> voteIndicator(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        VWAPIndicator vwap = new VWAPIndicator(series, VWAP_LEN);
        MoneyFlowIndexIndicator mfi = new MoneyFlowIndexIndicator(series, MFI_LEN);
        return new FunctionalIndicator(series, VWAP_LEN,
                i -> series.numFactory().numOf(state(close, vwap, mfi, i)));
    }

    private static double state(Indicator<Num> close, Indicator<Num> vwap, Indicator<Num> mfi, int i) {
        double c = SignalMath.safe(close, i);
        double v = SignalMath.safe(vwap, i);
        double m = SignalMath.safe(mfi, i);
        if (v <= 0.0 || m == 0.0) {
            return 0.0;
        }
        double priceVsVwap = (c - v) / v;
        double vwapComponent = Math.signum(priceVsVwap) * SignalMath.saturate(priceVsVwap * 30.0);
        double mfiBias = (m - 50.0) / 50.0; // [-1, +1]
        double combined = 0.6 * vwapComponent + 0.4 * SignalMath.clamp(mfiBias, -1.0, 1.0);
        return SignalMath.clamp(combined, -1.0, 1.0);
    }

    @Override
    List<Reason> describe(BarSeries series, int i, double g) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        VWAPIndicator vwap = new VWAPIndicator(series, VWAP_LEN);
        MoneyFlowIndexIndicator mfi = new MoneyFlowIndexIndicator(series, MFI_LEN);
        double c = SignalMath.safe(close, i);
        double v = SignalMath.safe(vwap, i);
        double m = SignalMath.safe(mfi, i);
        boolean bullish = g > 0;

        String condition = (bullish ? "Price above VWAP" : "Price below VWAP")
                + (m >= 50 ? ", MFI bullish" : ", MFI bearish");
        String narrative = "Money flow is %s: close %.2f is %s VWAP %.2f, MFI at %.1f."
                .formatted(bullish ? "supportive" : "negative", c, bullish ? "above" : "below", v, m);

        return List.of(new Reason(id(), "VWAP(14)+MFI(14)", condition,
                "close=%.2f, vwap=%.2f, mfi=%.1f".formatted(c, v, m),
                "price vs VWAP & MFI vs 50",
                SignalMath.round2(g), narrative));
    }
}
