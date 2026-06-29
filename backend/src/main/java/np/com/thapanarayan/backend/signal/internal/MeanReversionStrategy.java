package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S2 — Mean reversion (§6, table). Fades extremes: RSI(14) oversold while price
 * rides the lower Bollinger band → expect a bounce (bullish); RSI overbought at the
 * upper band → expect a fade (bearish). Both conditions must align — a lone
 * oversold RSI in the middle of the bands is not enough.
 */
@Component
class MeanReversionStrategy extends Ta4jStrategy {

    private static final int RSI_LEN = 14;
    private static final int BB_LEN = 20;
    private static final double BB_K = 2.0;

    @Override
    public StrategyId id() {
        return StrategyId.S2;
    }

    @Override
    int minBars() {
        return BB_LEN + 1;
    }

    @Override
    Indicator<Num> voteIndicator(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, RSI_LEN);
        BollingerBandFacade bb = new BollingerBandFacade(series, BB_LEN, BB_K);
        return new FunctionalIndicator(series, BB_LEN,
                i -> series.numFactory().numOf(state(close, rsi, bb.upper(), bb.lower(), i)));
    }

    private static double state(Indicator<Num> close, Indicator<Num> rsi,
            Indicator<Num> upper, Indicator<Num> lower, int i) {
        double r = SignalMath.safe(rsi, i);
        double c = SignalMath.safe(close, i);
        double up = SignalMath.safe(upper, i);
        double lo = SignalMath.safe(lower, i);
        double width = up - lo;
        if (r == 0.0 || width <= 0.0) {
            return 0.0;
        }
        double bandPos = (c - lo) / width; // 0 at lower band, 1 at upper band
        // Bullish: oversold RSI near the lower band.
        if (r < 35.0 && bandPos < 0.30) {
            double rsiF = SignalMath.clamp((35.0 - r) / 20.0, 0.0, 1.0);
            double bandF = SignalMath.clamp((0.30 - bandPos) / 0.30, 0.0, 1.0);
            return 0.6 * rsiF + 0.4 * bandF;
        }
        // Bearish: overbought RSI near the upper band.
        if (r > 65.0 && bandPos > 0.70) {
            double rsiF = SignalMath.clamp((r - 65.0) / 20.0, 0.0, 1.0);
            double bandF = SignalMath.clamp((bandPos - 0.70) / 0.30, 0.0, 1.0);
            return -(0.6 * rsiF + 0.4 * bandF);
        }
        return 0.0;
    }

    @Override
    List<Reason> describe(BarSeries series, int i, double g) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, RSI_LEN);
        BollingerBandFacade bb = new BollingerBandFacade(series, BB_LEN, BB_K);
        double r = SignalMath.safe(rsi, i);
        double c = SignalMath.safe(close, i);
        double up = SignalMath.safe(bb.upper(), i);
        double lo = SignalMath.safe(bb.lower(), i);
        boolean bullish = g > 0;

        String condition = bullish ? "RSI oversold at lower Bollinger band"
                : "RSI overbought at upper Bollinger band";
        String narrative = bullish
                ? "Stretched to the downside: RSI %.1f with price (%.2f) at the lower band (%.2f) — a reversion bounce is likely."
                        .formatted(r, c, lo)
                : "Stretched to the upside: RSI %.1f with price (%.2f) at the upper band (%.2f) — a pullback is likely."
                        .formatted(r, c, up);

        return List.of(new Reason(id(), "RSI(14)+BB(20,2)", condition,
                "RSI=%.1f, close=%.2f, band=[%.2f, %.2f]".formatted(r, c, lo, up),
                bullish ? "RSI<35 & price<=lower band" : "RSI>65 & price>=upper band",
                SignalMath.round2(g), narrative));
    }
}
