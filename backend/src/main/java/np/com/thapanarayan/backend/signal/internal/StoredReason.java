package np.com.thapanarayan.backend.signal.internal;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * Jackson-mapped persistence shape for one reason inside the {@code signal.votes}
 * JSONB. {@code observedValue}/{@code threshold} are stringified here (the
 * {@link Reason}'s free-form objects are formatted on the way in).
 */
record StoredReason(
        StrategyId strategyId,
        String indicator,
        String condition,
        String observedValue,
        String threshold,
        double contribution,
        String narrative) {
}
