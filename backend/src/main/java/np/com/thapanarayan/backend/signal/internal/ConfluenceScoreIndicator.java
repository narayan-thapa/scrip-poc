package np.com.thapanarayan.backend.signal.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * The confluence score exposed as a custom Ta4j {@link Indicator} (§6.4 backtesting
 * hook). Its value at each bar is the weighted blend of the Ta4j strategies' graded
 * votes, on the same [-100, +100] scale as the persisted signal score. This is what
 * lets Stage 6 backtest the whole confluence model with
 * {@code OverIndicatorRule(scoreIndicator, +T_buy)} /
 * {@code UnderIndicatorRule(scoreIndicator, -T_sell)} rather than only individual
 * strategies.
 *
 * <p>Only the Ta4j strategies (S1–S3, S5–S7) participate here: the custom S4/S8 read
 * floorsheet structures that are not reconstructed per historical bar, so they
 * contribute to the as-of-date signal but not to this replayable indicator.</p>
 */
final class ConfluenceScoreIndicator extends CachedIndicator<Num> {

    private final List<WeightedVote> votes;
    private final double totalWeight;

    private ConfluenceScoreIndicator(BarSeries series, List<WeightedVote> votes) {
        super(series);
        this.votes = votes;
        this.totalWeight = votes.stream().mapToDouble(WeightedVote::weight).sum();
    }

    /**
     * Builds the indicator over {@code series} from the supplied Ta4j strategies,
     * weighting each by its enabled confluence weight (strategies absent from
     * {@code weights} or with weight ≤ 0 are skipped).
     */
    static ConfluenceScoreIndicator forSeries(BarSeries series, List<Ta4jStrategy> strategies,
            Map<StrategyId, Double> weights) {
        List<WeightedVote> built = new ArrayList<>();
        for (Ta4jStrategy strategy : strategies) {
            double weight = weights.getOrDefault(strategy.id(), 0.0);
            if (weight > 0.0) {
                built.add(new WeightedVote(weight, strategy.voteIndicator(series)));
            }
        }
        return new ConfluenceScoreIndicator(series, built);
    }

    @Override
    protected Num calculate(int index) {
        if (totalWeight <= 0.0) {
            return getBarSeries().numFactory().zero();
        }
        double numerator = 0.0;
        for (WeightedVote wv : votes) {
            numerator += wv.weight() * SignalMath.safe(wv.vote(), index);
        }
        double score = SignalMath.clamp(100.0 * numerator / totalWeight, -100.0, 100.0);
        return getBarSeries().numFactory().numOf(score);
    }

    @Override
    public int getCountOfUnstableBars() {
        return votes.stream().mapToInt(wv -> wv.vote().getCountOfUnstableBars()).max().orElse(0);
    }

    private record WeightedVote(double weight, Indicator<Num> vote) {
    }
}
