package np.com.thapanarayan.backend.signal.api;

import java.util.List;

/**
 * One strategy's contribution to a signal, exposed so the UI can show the full
 * breakdown ("4 of 6 trend/volume strategies bullish; mean-reversion dissenting").
 *
 * @param strategyId   the voting strategy
 * @param label        human-readable strategy name
 * @param vote         direction ∈ {-1, 0, +1} (bearish / neutral / bullish)
 * @param confidence   graded conviction ∈ [0, 1]
 * @param weight       the configured confluence weight applied to this vote
 * @param contribution {@code weight * vote * confidence} — this vote's signed pull on the score
 * @param applicable   whether the strategy had enough data to vote (false → excluded from the blend)
 * @param reasons      the structured reasons backing the vote
 */
public record StrategyVoteView(
        StrategyId strategyId,
        String label,
        double vote,
        double confidence,
        double weight,
        double contribution,
        boolean applicable,
        List<ReasonView> reasons) {
}
