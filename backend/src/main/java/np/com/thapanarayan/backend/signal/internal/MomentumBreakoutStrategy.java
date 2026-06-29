package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelLowerIndicator;
import org.ta4j.core.indicators.donchian.DonchianChannelUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S3 — Momentum breakout (§6, table). Close pushing through the Donchian(20) high
 * is bullish, through the low is bearish; the move is graded up by a volume surge
 * over the 20-day average volume (a breakout on thin volume is treated cautiously).
 */
@Component
class MomentumBreakoutStrategy extends Ta4jStrategy {

    private static final int CHANNEL = 20;
    private static final int VOL_AVG = 20;
    private static final double EDGE = 0.80; // fraction of the channel that counts as "at the edge"

    @Override
    public StrategyId id() {
        return StrategyId.S3;
    }

    @Override
    int minBars() {
        return CHANNEL + 1;
    }

    @Override
    Indicator<Num> voteIndicator(BarSeries series) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        DonchianChannelUpperIndicator upper = new DonchianChannelUpperIndicator(series, CHANNEL);
        DonchianChannelLowerIndicator lower = new DonchianChannelLowerIndicator(series, CHANNEL);
        SMAIndicator volAvg = new SMAIndicator(new VolumeIndicator(series), VOL_AVG);
        VolumeIndicator volume = new VolumeIndicator(series);
        return new FunctionalIndicator(series, CHANNEL,
                i -> series.numFactory().numOf(state(close, upper, lower, volume, volAvg, i)));
    }

    private static double state(Indicator<Num> close, Indicator<Num> upper, Indicator<Num> lower,
            Indicator<Num> volume, Indicator<Num> volAvg, int i) {
        double c = SignalMath.safe(close, i);
        double up = SignalMath.safe(upper, i);
        double lo = SignalMath.safe(lower, i);
        double width = up - lo;
        if (width <= 0.0) {
            return 0.0;
        }
        double pos = (c - lo) / width; // 0 at channel low, 1 at channel high
        double avgVol = SignalMath.safe(volAvg, i);
        double volRatio = avgVol > 0 ? SignalMath.safe(volume, i) / avgVol : 1.0;
        double volFactor = SignalMath.clamp(volRatio - 1.0, 0.0, 1.0); // 0 at avg, 1 at 2x+
        double conviction = 0.5 + 0.5 * volFactor;

        if (pos >= EDGE) {
            return SignalMath.clamp((pos - EDGE) / (1.0 - EDGE), 0.0, 1.0) * conviction;
        }
        if (pos <= 1.0 - EDGE) {
            return -SignalMath.clamp(((1.0 - EDGE) - pos) / (1.0 - EDGE), 0.0, 1.0) * conviction;
        }
        return 0.0;
    }

    @Override
    List<Reason> describe(BarSeries series, int i, double g) {
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        DonchianChannelUpperIndicator upper = new DonchianChannelUpperIndicator(series, CHANNEL);
        DonchianChannelLowerIndicator lower = new DonchianChannelLowerIndicator(series, CHANNEL);
        SMAIndicator volAvg = new SMAIndicator(new VolumeIndicator(series), VOL_AVG);
        double c = SignalMath.safe(close, i);
        double up = SignalMath.safe(upper, i);
        double lo = SignalMath.safe(lower, i);
        double avgVol = SignalMath.safe(volAvg, i);
        double vol = series.getBar(i).getVolume().doubleValue();
        double volRatio = avgVol > 0 ? vol / avgVol : 1.0;
        boolean bullish = g > 0;

        String condition = bullish ? "Close breaking Donchian(20) high" : "Close breaking Donchian(20) low";
        String narrative = ("%s breakout: close %.2f against the %d-day channel [%.2f, %.2f] on %.1fx average volume.")
                .formatted(bullish ? "Bullish" : "Bearish", c, CHANNEL, lo, up, volRatio);

        return List.of(new Reason(id(), "Donchian(20)+Volume", condition,
                "close=%.2f, channel=[%.2f, %.2f], vol=%.1fx avg".formatted(c, lo, up, volRatio),
                bullish ? "close near 20d high + volume surge" : "close near 20d low + volume surge",
                SignalMath.round2(g), narrative));
    }
}
