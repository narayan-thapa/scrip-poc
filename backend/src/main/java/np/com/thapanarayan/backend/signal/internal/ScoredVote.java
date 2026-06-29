package np.com.thapanarayan.backend.signal.internal;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * One strategy's vote paired with the confluence weight applied to it. {@code
 * contribution} is the vote's signed pull on the score ({@code weight * vote *
 * confidence}); the scorer normalises the sum of these by the applicable weights.
 *
 * @param applicable whether this vote was counted in the blend (had data and weight > 0)
 */
record ScoredVote(
        StrategyId id,
        String label,
        StrategyVote vote,
        double weight,
        double contribution,
        boolean applicable) {
}
