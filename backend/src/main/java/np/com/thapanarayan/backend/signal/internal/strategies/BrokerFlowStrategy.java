package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

/**
 * S8 — Broker accumulation/distribution (NEPSE-specific): high buyer concentration on an up day
 * suggests accumulation (bullish); high seller concentration on a down day suggests distribution
 * (bearish). Concentration is suggestive (one broker aggregates many clients), so confidence is capped.
 */
@Component
public class BrokerFlowStrategy implements SignalStrategy {

    private static final double CONCENTRATION_THRESHOLD = 0.40;
    private static final double MAX_CONFIDENCE = 0.6;

    @Override
    public String id() {
        return "S8";
    }

    @Override
    public String name() {
        return "Broker Accumulation/Distribution";
    }

    @Override
    public double defaultWeight() {
        return 0.8; // suggestive input, weighted slightly lower
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        if (ctx.brokerFlow().isEmpty()) {
            return StrategyVote.neutral();
        }
        BrokerFlowView flow = ctx.brokerFlow().get();
        var close = new ClosePriceIndicator(ctx.series());
        int i = ctx.index();
        if (i < 1) {
            return StrategyVote.neutral();
        }
        double price = Strategies.val(close, i);
        double prev = Strategies.val(close, i - 1);
        if (Strategies.anyNaN(price, prev)) {
            return StrategyVote.neutral();
        }
        int dayDirection = price > prev ? 1 : price < prev ? -1 : 0;

        if (flow.topBuyerShare() >= CONCENTRATION_THRESHOLD && dayDirection > 0) {
            double confidence = Math.min(MAX_CONFIDENCE, flow.topBuyerShare());
            return StrategyVote.bullish(confidence, new Reason(id(), "Broker Flow", "buyer concentration on up day",
                    "topBuyerShare=%s, HHI_buy=%s".formatted(Strategies.r(flow.topBuyerShare()), Strategies.r(flow.hhiBuy())),
                    "topBuyerShare≥0.40 & up day", confidence,
                    "Concentrated buying (top broker %s of buys) into a rising price — accumulation.".formatted(pct(flow.topBuyerShare()))));
        }
        if (flow.topSellerShare() >= CONCENTRATION_THRESHOLD && dayDirection <= 0) {
            double confidence = Math.min(MAX_CONFIDENCE, flow.topSellerShare());
            return StrategyVote.bearish(confidence, new Reason(id(), "Broker Flow", "seller concentration on down day",
                    "topSellerShare=%s, HHI_sell=%s".formatted(Strategies.r(flow.topSellerShare()), Strategies.r(flow.hhiSell())),
                    "topSellerShare≥0.40 & down/flat day", -confidence,
                    "Concentrated selling (top broker %s of sells) into a falling price — distribution.".formatted(pct(flow.topSellerShare()))));
        }
        return StrategyVote.neutral();
    }

    private static String pct(double v) {
        return String.format("%.0f%%", v * 100);
    }
}
