package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.supertrend.SuperTrendIndicator;

/** S6 — Supertrend: price above/below the ATR band defines the trend side. */
@Component
public class SupertrendStrategy implements SignalStrategy {

    @Override
    public String id() {
        return "S6";
    }

    @Override
    public String name() {
        return "Supertrend";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        var series = ctx.series();
        int i = ctx.index();
        double price = Strategies.val(new ClosePriceIndicator(series), i);
        double st = Strategies.val(new SuperTrendIndicator(series, 10, 3.0), i);
        if (Strategies.anyNaN(price, st) || price == 0) {
            return StrategyVote.neutral();
        }
        double vote = price > st ? 1 : price < st ? -1 : 0;
        double confidence = Strategies.clamp01(Math.abs(price - st) / (price * 0.05));
        String dir = vote > 0 ? "price above Supertrend" : "price below Supertrend";
        return StrategyVote.graded(vote, confidence, new Reason(id(), "Supertrend(10,3)", dir,
                "close=%s, ST=%s".formatted(Strategies.r(price), Strategies.r(st)), "price vs band",
                vote * confidence, "Trend is %s (%s).".formatted(vote > 0 ? "up" : "down", dir)));
    }
}
