package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * S4 — Volume Profile (NEPSE floorsheet-native, not Ta4j): acceptance above the Value Area High is
 * bullish, rejection below the Value Area Low is bearish; inside the value area is neutral.
 */
@Component
public class VolumeProfileStrategy implements SignalStrategy {

    @Override
    public String id() {
        return "S4";
    }

    @Override
    public String name() {
        return "Volume Profile";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        if (ctx.volumeProfile().isEmpty()) {
            return StrategyVote.neutral();
        }
        VolumeProfileView vp = ctx.volumeProfile().get();
        double price = Strategies.val(new ClosePriceIndicator(ctx.series()), ctx.index());
        double vah = vp.vah().doubleValue();
        double val = vp.val().doubleValue();
        if (Strategies.anyNaN(price) || vah <= val) {
            return StrategyVote.neutral();
        }
        double range = vah - val;
        if (price > vah) {
            double confidence = Strategies.clamp01((price - vah) / range);
            return StrategyVote.bullish(confidence, new Reason(id(), "Volume Profile", "close above VAH",
                    "close=%s, VAH=%s, POC=%s".formatted(Strategies.r(price), Strategies.r(vah), Strategies.r(vp.poc().doubleValue())),
                    "close>VAH", confidence,
                    "Trading above the value area high (%s) — acceptance higher.".formatted(Strategies.r(vah))));
        }
        if (price < val) {
            double confidence = Strategies.clamp01((val - price) / range);
            return StrategyVote.bearish(confidence, new Reason(id(), "Volume Profile", "close below VAL",
                    "close=%s, VAL=%s, POC=%s".formatted(Strategies.r(price), Strategies.r(val), Strategies.r(vp.poc().doubleValue())),
                    "close<VAL", -confidence,
                    "Trading below the value area low (%s) — acceptance lower.".formatted(Strategies.r(val))));
        }
        return StrategyVote.neutral();
    }
}
