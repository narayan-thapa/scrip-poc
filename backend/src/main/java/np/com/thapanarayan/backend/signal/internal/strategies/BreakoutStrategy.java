package np.com.thapanarayan.backend.signal.internal.strategies;

import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalStrategy;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.springframework.stereotype.Component;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;

/** S3 — Momentum breakout: close breaks the Donchian(20) channel, confirmed by a volume surge. */
@Component
public class BreakoutStrategy implements SignalStrategy {

    private static final int N = 20;
    private static final double VOLUME_MULT = 1.5;

    @Override
    public String id() {
        return "S3";
    }

    @Override
    public String name() {
        return "Momentum Breakout";
    }

    @Override
    public StrategyVote evaluate(SymbolContext ctx) {
        var series = ctx.series();
        int i = ctx.index();
        var close = new ClosePriceIndicator(series);
        double price = Strategies.val(close, i);
        double upper = Strategies.val(new DonchianChannelUpperIndicator(series, N), i);
        double lower = Strategies.val(new DonchianChannelLowerIndicator(series, N), i);
        double vol = Strategies.val(new VolumeIndicator(series), i);
        double volSma = Strategies.val(new SMAIndicator(new VolumeIndicator(series), N), i);
        if (Strategies.anyNaN(price, upper, lower, vol, volSma) || volSma == 0) {
            return StrategyVote.neutral();
        }
        double volRatio = vol / volSma;
        boolean volumeConfirmed = volRatio >= VOLUME_MULT;

        if (price >= upper && volumeConfirmed) {
            double confidence = Strategies.clamp01((volRatio - 1) / 2);
            return StrategyVote.bullish(confidence, new Reason(id(), "Donchian(20)+Volume", "Close at channel high",
                    "close=%s, upper=%s, vol×=%s".formatted(Strategies.r(price), Strategies.r(upper), Strategies.r(volRatio)),
                    "close≥Donchian high & vol>1.5×", confidence,
                    "Breakout to a %d-day high on %s× average volume.".formatted(N, Strategies.r(volRatio))));
        }
        if (price <= lower && volumeConfirmed) {
            double confidence = Strategies.clamp01((volRatio - 1) / 2);
            return StrategyVote.bearish(confidence, new Reason(id(), "Donchian(20)+Volume", "Close at channel low",
                    "close=%s, lower=%s, vol×=%s".formatted(Strategies.r(price), Strategies.r(lower), Strategies.r(volRatio)),
                    "close≤Donchian low & vol>1.5×", -confidence,
                    "Breakdown to a %d-day low on %s× average volume.".formatted(N, Strategies.r(volRatio))));
        }
        return StrategyVote.neutral();
    }
}
