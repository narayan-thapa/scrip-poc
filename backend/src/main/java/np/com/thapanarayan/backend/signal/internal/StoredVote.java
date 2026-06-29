package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * Jackson-mapped persistence shape for one strategy's vote inside the
 * {@code signal.votes} JSONB array. Stores the full breakdown — direction,
 * confidence, applied weight, contribution, and reasons — so the signal stays
 * auditable without recomputation.
 */
record StoredVote(
        StrategyId strategyId,
        String label,
        double vote,
        double confidence,
        double weight,
        double contribution,
        boolean applicable,
        List<StoredReason> reasons) {
}
