package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator;

/**
 * S7 — Money flow: Chaikin Money Flow direction + MFI, aligned with price relative to its mean (a
 * VWAP proxy on a daily series, which has no intraday tape). Bullish when flow, MFI and trend agree.
 */
@Component
public class MoneyFlowStrategy implements SignalStrategy {

    @Override
    public String id() {
        return "S7";
    }

    @Override
    public String name() {
        return "VWAP / Money Flow";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        var series = ctx.series();
        int i = ctx.index();
        var close = new ClosePriceIndicator(series);
        double price = Strategies.val(close, i);
        double sma = Strategies.val(new SMAIndicator(close, 20), i);
        double cmf = Strategies.val(new ChaikinMoneyFlowIndicator(series, 20), i);
        double mfi = Strategies.val(new MoneyFlowIndexIndicator(series, 14), i);
        if (Strategies.anyNaN(price, sma, cmf, mfi)) {
            return StrategyVote.neutral();
        }
        int trend = price > sma ? 1 : price < sma ? -1 : 0;
        int flow = cmf > 0 ? 1 : cmf < 0 ? -1 : 0;
        boolean mfiAgrees = (trend > 0 && mfi >= 50) || (trend < 0 && mfi <= 50);
        if (trend == 0 || trend != flow || !mfiAgrees) {
            return StrategyVote.neutral();
        }
        double confidence = Strategies.clamp01(Math.abs(cmf) * 2 + Math.abs(mfi - 50) / 50 * 0.5);
        return StrategyVote.graded(trend, confidence, new Reason(id(), "CMF+MFI+SMA(20)",
                trend > 0 ? "money flow in, price above mean" : "money flow out, price below mean",
                "CMF=%s, MFI=%s, close=%s, SMA20=%s".formatted(Strategies.r(cmf), Strategies.r(mfi),
                        Strategies.r(price), Strategies.r(sma)),
                "flow & MFI align with trend", trend * confidence,
                "%s money flow (CMF %s, MFI %s) confirming price %s its mean.".formatted(
                        trend > 0 ? "Positive" : "Negative", Strategies.r(cmf), Strategies.r(mfi),
                        trend > 0 ? "above" : "below")));
    }
}
