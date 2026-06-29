package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S4 — Volume Profile (§6.2). Custom (not Ta4j): reads the true volume-at-price
 * profile built from the floorsheet. Acceptance <em>above</em> the value-area high
 * is bullish, rejection <em>below</em> the value-area low is bearish; inside the
 * value area, price is pulled toward the Point of Control (mild mean reversion).
 * Abstains when no profile exists for the date.
 */
@Component
class VolumeProfileStrategy implements SignalStrategy {

    @Override
    public StrategyId id() {
        return StrategyId.S4;
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        VolumeProfileView vp = ctx.volumeProfile();
        if (vp == null || vp.valueAreaHigh() == null || vp.valueAreaLow() == null || vp.poc() == null) {
            return StrategyVote.notApplicable();
        }
        double close = ctx.series().getBar(ctx.endIndex()).getClosePrice().doubleValue();
        double vah = vp.valueAreaHigh().doubleValue();
        double val = vp.valueAreaLow().doubleValue();
        double poc = vp.poc().doubleValue();
        double vaWidth = vah - val;
        if (vaWidth <= 0.0) {
            return StrategyVote.notApplicable();
        }

        if (close > vah) {
            double conf = SignalMath.saturate((close - vah) / vaWidth);
            return StrategyVote.bullish(conf, List.of(reason(
                    "Close above value-area high", close, vah, poc, conf,
                    "Acceptance above the value area (VAH %.2f): close %.2f signals a bullish breakout from the volume shelf."
                            .formatted(vah, close))));
        }
        if (close < val) {
            double conf = SignalMath.saturate((val - close) / vaWidth);
            return StrategyVote.bearish(conf, List.of(reason(
                    "Close below value-area low", close, val, poc, -conf,
                    "Rejection below the value area (VAL %.2f): close %.2f signals bearish distribution."
                            .formatted(val, close))));
        }
        // Inside the value area: mild reversion toward the Point of Control.
        double dist = (poc - close) / vaWidth; // >0 when below POC (room to rise)
        double conf = 0.3 * SignalMath.saturate(dist);
        if (conf == 0.0) {
            return StrategyVote.neutral();
        }
        double vote = Math.signum(dist);
        String dir = vote > 0 ? "below" : "above";
        return new StrategyVote(vote, conf, true, List.of(reason(
                "Price inside value area, " + dir + " POC", close, poc, poc, vote * conf,
                "Price %.2f sits %s the Point of Control %.2f inside the value area — mild pull toward POC."
                        .formatted(close, dir, poc))));
    }

    private Reason reason(String condition, double close, double level, double poc,
            double contribution, String narrative) {
        return new Reason(id(), "Volume Profile (POC/VA)", condition,
                "close=%.2f, POC=%.2f, level=%.2f".formatted(close, poc, level),
                "value-area edge / POC",
                SignalMath.round2(contribution), narrative);
    }
}
