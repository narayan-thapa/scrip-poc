package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/** S1 — Trend-following: EMA(9) vs EMA(21), gated by ADX trend strength. */
@Component
public class TrendFollowingStrategy implements SignalStrategy {

    @Override
    public String id() {
        return "S1";
    }

    @Override
    public String name() {
        return "Trend Following";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        var series = ctx.series();
        int i = ctx.index();
        var close = new ClosePriceIndicator(series);
        double e9 = Strategies.val(new EMAIndicator(close, 9), i);
        double e21 = Strategies.val(new EMAIndicator(close, 21), i);
        double adx = Strategies.val(new ADXIndicator(series, 14), i);
        if (Strategies.anyNaN(e9, e21, adx) || e21 == 0) {
            return StrategyVote.neutral();
        }
        double gap = (e9 - e21) / e21;
        double trendGate = adx >= 20 ? 1.0 : 0.4;
        double confidence = Strategies.clamp01(Math.abs(gap) * 20) * trendGate;
        double vote = Math.signum(gap);

        String dir = vote > 0 ? "EMA9 above EMA21" : vote < 0 ? "EMA9 below EMA21" : "flat";
        Reason reason = new Reason(id(), "EMA(9/21)+ADX", dir,
                "EMA9=%s, EMA21=%s, ADX=%s".formatted(Strategies.r(e9), Strategies.r(e21), Strategies.r(adx)),
                "cross & ADX>20", vote * confidence,
                "%s with ADX %s %s.".formatted(dir, Strategies.r(adx), adx >= 20 ? "confirming trend" : "(weak/choppy)"));
        return StrategyVote.graded(vote, confidence, reason);
    }
}
