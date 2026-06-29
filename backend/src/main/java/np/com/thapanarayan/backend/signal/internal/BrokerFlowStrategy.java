package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S8 — Broker accumulation / distribution (§6.3), a NEPSE-specific edge. Custom
 * (not Ta4j): reads the per-broker net flow. High buyer concentration on a rising
 * or flat day reads as accumulation (bullish); high seller concentration on a
 * falling or flat day reads as distribution (bearish).
 *
 * <p>Per §6.3, a single broker often aggregates many clients, so concentration is
 * <em>suggestive, not conclusive</em>: the vote is damped when price disagrees and
 * the strategy carries a below-average default weight. The caveat is stated in the
 * narrative.</p>
 */
@Component
class BrokerFlowStrategy implements SignalStrategy {

    private static final double CONC_FLOOR = 0.30; // below this share, concentration is unremarkable
    private static final double CONC_SPAN = 0.40;
    private static final double FLAT_THRESHOLD = 0.005; // ±0.5% counts as a flat day

    @Override
    public StrategyId id() {
        return StrategyId.S8;
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        BrokerFlowView bf = ctx.brokerFlow();
        if (bf == null) {
            return StrategyVote.notApplicable();
        }
        double buyShare = bf.topBuyerShare() == null ? 0.0 : bf.topBuyerShare().doubleValue();
        double sellShare = bf.topSellerShare() == null ? 0.0 : bf.topSellerShare().doubleValue();
        double priceChange = priceChange(ctx);

        boolean accumulation = buyShare >= sellShare;
        double share = accumulation ? buyShare : sellShare;
        double concentration = SignalMath.clamp((share - CONC_FLOOR) / CONC_SPAN, 0.0, 1.0);
        if (concentration == 0.0) {
            return StrategyVote.neutral();
        }
        double priceFactor = priceAgreement(accumulation, priceChange);
        double conf = concentration * priceFactor;
        if (conf == 0.0) {
            return StrategyVote.neutral();
        }

        if (accumulation) {
            Integer broker = bf.topBuyerBroker();
            return StrategyVote.bullish(conf, List.of(reason(
                    "High buyer concentration", broker, buyShare, priceChange, conf,
                    ("Possible accumulation: top buyer (broker %s) is %.0f%% of buy volume on a %s day. "
                            + "Concentration is suggestive — one broker may aggregate many clients.")
                            .formatted(label(broker), buyShare * 100, dayLabel(priceChange)))));
        }
        Integer broker = bf.topSellerBroker();
        return StrategyVote.bearish(conf, List.of(reason(
                "High seller concentration", broker, sellShare, priceChange, -conf,
                ("Possible distribution: top seller (broker %s) is %.0f%% of sell volume on a %s day. "
                        + "Concentration is suggestive — one broker may aggregate many clients.")
                        .formatted(label(broker), sellShare * 100, dayLabel(priceChange)))));
    }

    /** Day's close-over-close change; 0 when there is no prior bar. */
    private static double priceChange(SymbolContext ctx) {
        int i = ctx.endIndex();
        if (i < 1) {
            return 0.0;
        }
        double close = ctx.series().getBar(i).getClosePrice().doubleValue();
        double prev = ctx.series().getBar(i - 1).getClosePrice().doubleValue();
        return prev == 0.0 ? 0.0 : (close - prev) / prev;
    }

    /** Accumulation wants up/flat days; distribution wants down/flat days. */
    private static double priceAgreement(boolean accumulation, double change) {
        boolean flat = Math.abs(change) <= FLAT_THRESHOLD;
        if (flat) {
            return 0.6;
        }
        boolean up = change > 0;
        return (accumulation == up) ? 1.0 : 0.2;
    }

    private static String dayLabel(double change) {
        if (Math.abs(change) <= FLAT_THRESHOLD) {
            return "flat";
        }
        return change > 0 ? "rising" : "falling";
    }

    private static String label(Integer broker) {
        return broker == null ? "?" : broker.toString();
    }

    private Reason reason(String condition, Integer broker, double share, double change,
            double contribution, String narrative) {
        return new Reason(id(), "Broker flow concentration", condition,
                "broker=%s, share=%.0f%%, change=%.2f%%".formatted(label(broker), share * 100, change * 100),
                "top-broker share > %.0f%%".formatted(CONC_FLOOR * 100),
                SignalMath.round2(contribution), narrative);
    }
}
