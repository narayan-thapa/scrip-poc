package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * S9 — Confluence scoring (§6.4). Blends the per-strategy graded votes into a single
 * score ∈ [-100, +100] and maps it to an action against the configured thresholds:
 *
 * <pre>
 *   score  = 100 * Σ(w_i · vote_i · confidence_i) / Σ(w_i)      over applicable strategies
 *   action = BUY  if score >= +T_buy
 *            SELL if score <= -T_sell
 *            HOLD otherwise
 * </pre>
 *
 * <p>The denominator includes every <em>applicable</em> strategy's weight — a
 * strategy that ran but stayed neutral still dampens the score, so a strong reading
 * requires genuine agreement, not just one loud vote. Strategies that could not run
 * (warm-up / missing structure) are excluded entirely.</p>
 */
@Component
class ConfluenceScorer {

    private final SignalProperties properties;

    ConfluenceScorer(SignalProperties properties) {
        this.properties = properties;
    }

    /**
     * @param votes   per-strategy votes, in catalog order for a stable breakdown
     * @param weights enabled strategy → weight (disabled/absent strategies are not blended)
     */
    ConfluenceResult score(Map<StrategyId, StrategyVote> votes, Map<StrategyId, Double> weights) {
        List<ScoredVote> scored = new ArrayList<>(votes.size());
        double numerator = 0.0;
        double denominator = 0.0;

        for (Map.Entry<StrategyId, StrategyVote> e : votes.entrySet()) {
            StrategyId id = e.getKey();
            StrategyVote vote = e.getValue();
            double weight = weights.getOrDefault(id, 0.0);
            boolean applicable = vote.applicable() && weight > 0.0;
            double contribution = weight * vote.signedContribution();
            if (applicable) {
                numerator += contribution;
                denominator += weight;
            }
            scored.add(new ScoredVote(id, id.label(), vote, weight,
                    SignalMath.round2(applicable ? contribution : 0.0), applicable));
        }

        double raw = denominator > 0.0 ? 100.0 * numerator / denominator : 0.0;
        BigDecimal score = BigDecimal.valueOf(SignalMath.clamp(raw, -100.0, 100.0))
                .setScale(2, RoundingMode.HALF_UP);
        return new ConfluenceResult(score, action(score), scored);
    }

    private SignalAction action(BigDecimal score) {
        if (score.doubleValue() >= properties.buyThreshold()) {
            return SignalAction.BUY;
        }
        if (score.doubleValue() <= -properties.sellThreshold()) {
            return SignalAction.SELL;
        }
        return SignalAction.HOLD;
    }
}
