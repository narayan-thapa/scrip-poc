package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Base for the Ta4j-backed strategies (S1–S3, S5–S7). The subclass builds its
 * indicators and expresses a <em>graded directional state</em> ∈ [-1, +1] as a
 * per-bar {@link Indicator} — sign is the direction, magnitude the conviction. The
 * as-of-date {@link #evaluate(SymbolContext)} reads that indicator at the series end
 * index and splits it into {@code vote} (sign) and {@code confidence} (magnitude);
 * the very same indicator is what {@link ConfluenceScoreIndicator} replays over
 * history, so signal and backtest can never diverge.
 *
 * <p>A state (not an event-spike) is used deliberately: it answers "is the symbol in
 * a bullish trend/regime today and how strongly", which both blends sensibly and
 * replays for backtesting — whereas a bare cross fires on a single bar only.</p>
 */
abstract class Ta4jStrategy implements SignalStrategy {

    @Override
    public final StrategyVote evaluate(SymbolContext ctx) {
        BarSeries series = ctx.series();
        if (series.getBarCount() < minBars()) {
            return StrategyVote.notApplicable();
        }
        int i = series.getEndIndex();
        double g = SignalMath.safe(voteIndicator(series), i);
        if (g == 0.0) {
            return StrategyVote.neutral();
        }
        double vote = Math.signum(g);
        double confidence = Math.abs(g);
        return new StrategyVote(vote, confidence, true, describe(series, i, g));
    }

    /** Minimum bars before the strategy's longest look-back is meaningfully warmed up. */
    abstract int minBars();

    /** The graded directional-state indicator, value ∈ [-1, +1] at each bar. */
    abstract Indicator<Num> voteIndicator(BarSeries series);

    /** Structured reasons for the vote at {@code index}, given its signed contribution. */
    abstract List<Reason> describe(BarSeries series, int index, double signedContribution);
}
