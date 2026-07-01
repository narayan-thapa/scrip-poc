package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/** S5 — MACD line vs signal line, with zero-line context. */
@Component
public class MacdStrategy implements SignalStrategy {

    @Override
    public String id() {
        return "S5";
    }

    @Override
    public String name() {
        return "MACD";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        var series = ctx.series();
        int i = ctx.index();
        var close = new ClosePriceIndicator(series);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        double macdV = Strategies.val(macd, i);
        double signalV = Strategies.val(macd.getSignalLine(9), i);
        double price = Strategies.val(close, i);
        if (Strategies.anyNaN(macdV, signalV, price) || price == 0) {
            return StrategyVote.neutral();
        }
        double hist = macdV - signalV;
        double vote = Math.signum(hist);
        double zeroLineBoost = (macdV > 0) == (vote > 0) ? 1.0 : 0.6;
        double confidence = Strategies.clamp01(Math.abs(hist) / (price * 0.005)) * zeroLineBoost;

        String dir = vote > 0 ? "MACD above signal" : "MACD below signal";
        return StrategyVote.graded(vote, confidence, new Reason(id(), "MACD(12/26/9)", dir,
                "MACD=%s, signal=%s, hist=%s".formatted(Strategies.r(macdV), Strategies.r(signalV), Strategies.r(hist)),
                "MACD×signal cross", vote * confidence,
                "%s (histogram %s), %s the zero line.".formatted(dir, Strategies.r(hist), macdV > 0 ? "above" : "below")));
    }
}
