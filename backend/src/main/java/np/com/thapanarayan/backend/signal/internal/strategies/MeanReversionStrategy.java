package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;

/** S2 — Mean reversion: RSI oversold/overbought confirmed by price at a Bollinger band. */
@Component
public class MeanReversionStrategy implements SignalStrategy {

    @Override
    public String id() {
        return "S2";
    }

    @Override
    public String name() {
        return "Mean Reversion";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        var series = ctx.series();
        int i = ctx.index();
        var close = new ClosePriceIndicator(series);
        var middle = new BollingerBandsMiddleIndicator(new SMAIndicator(close, 20));
        var sd = new StandardDeviationIndicator(close, 20);
        var k = series.numFactory().numOf(2);
        double upper = Strategies.val(new BollingerBandsUpperIndicator(middle, sd, k), i);
        double lower = Strategies.val(new BollingerBandsLowerIndicator(middle, sd, k), i);
        double rsi = Strategies.val(new RSIIndicator(close, 14), i);
        double price = Strategies.val(close, i);
        if (Strategies.anyNaN(upper, lower, rsi, price)) {
            return StrategyVote.neutral();
        }

        if (rsi < 30 && price <= lower) {
            double confidence = Strategies.clamp01((30 - rsi) / 30 + 0.3);
            return StrategyVote.bullish(confidence, new Reason(id(), "RSI+Bollinger", "RSI<30 at lower band",
                    "RSI=%s, close=%s, lower=%s".formatted(Strategies.r(rsi), Strategies.r(price), Strategies.r(lower)),
                    "RSI<30 & price≤lower", confidence,
                    "Oversold (RSI %s) at the lower Bollinger band — mean-reversion bounce likely.".formatted(Strategies.r(rsi))));
        }
        if (rsi > 70 && price >= upper) {
            double confidence = Strategies.clamp01((rsi - 70) / 30 + 0.3);
            return StrategyVote.bearish(confidence, new Reason(id(), "RSI+Bollinger", "RSI>70 at upper band",
                    "RSI=%s, close=%s, upper=%s".formatted(Strategies.r(rsi), Strategies.r(price), Strategies.r(upper)),
                    "RSI>70 & price≥upper", -confidence,
                    "Overbought (RSI %s) at the upper Bollinger band — pullback likely.".formatted(Strategies.r(rsi))));
        }
        return StrategyVote.neutral();
    }
}
