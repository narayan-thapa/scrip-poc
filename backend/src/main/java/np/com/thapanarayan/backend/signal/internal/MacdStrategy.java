package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S5 — MACD (§6, table). The MACD(12,26) line versus its signal(9) sets the
 * direction; the histogram, normalised by ATR(14) so it is comparable across price
 * scales, grades the conviction. Agreement with the zero line (both above → trend
 * up) amplifies; disagreement damps.
 */
@Component
class MacdStrategy extends Ta4jStrategy {

    private static final int SHORT = 12;
    private static final int LONG = 26;
    private static final int SIGNAL = 9;
    private static final int ATR_LEN = 14;

    @Override
    public StrategyId id() {
        return StrategyId.S5;
    }

    @Override
    int minBars() {
        return LONG + SIGNAL;
    }

    @Override
    Indicator<Num> voteIndicator(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(close, SHORT, LONG);
        EMAIndicator signal = new EMAIndicator(macd, SIGNAL);
        ATRIndicator atr = new ATRIndicator(series, ATR_LEN);
        return new FunctionalIndicator(series, LONG + SIGNAL,
                i -> series.numFactory().numOf(state(macd, signal, atr, i)));
    }

    private static double state(Indicator<Num> macd, Indicator<Num> signal, Indicator<Num> atr, int i) {
        double m = SignalMath.safe(macd, i);
        double s = SignalMath.safe(signal, i);
        double a = SignalMath.safe(atr, i);
        if (a <= 0.0) {
            return 0.0;
        }
        double hist = m - s;
        double conf = SignalMath.saturate(hist / a);
        double zeroLineAgreement = Math.signum(hist) == Math.signum(m) ? 1.0 : 0.7;
        return Math.signum(hist) * conf * zeroLineAgreement;
    }

    @Override
    List<Reason> describe(BarSeries series, int i, double g) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(close, SHORT, LONG);
        EMAIndicator signal = new EMAIndicator(macd, SIGNAL);
        double m = SignalMath.safe(macd, i);
        double s = SignalMath.safe(signal, i);
        double hist = m - s;
        boolean bullish = g > 0;

        String condition = (bullish ? "MACD above signal" : "MACD below signal")
                + (m >= 0 ? ", above zero line" : ", below zero line");
        String narrative = "MACD momentum is %s: line %.3f vs signal %.3f (histogram %.3f), %s the zero line."
                .formatted(bullish ? "positive" : "negative", m, s, hist, m >= 0 ? "above" : "below");

        return List.of(new Reason(id(), "MACD(12,26,9)", condition,
                "macd=%.3f, signal=%.3f, hist=%.3f".formatted(m, s, hist),
                "MACD %s signal".formatted(bullish ? ">" : "<"),
                SignalMath.round2(g), narrative));
    }
}
