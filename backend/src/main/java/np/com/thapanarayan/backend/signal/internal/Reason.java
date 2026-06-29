package np.com.thapanarayan.backend.signal.internal;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * Internal structured reason produced by a strategy (§6.1). Mirrors the published
 * {@link np.com.thapanarayan.backend.signal.api.ReasonView} but keeps
 * {@code observedValue}/{@code threshold} as free {@link Object}s so strategies can
 * pass numbers directly; they are stringified on the way out.
 *
 * @param contribution signed contribution, {@code vote * confidence} ∈ [-1, +1]
 */
record Reason(
        StrategyId strategyId,
        String indicator,
        String condition,
        Object observedValue,
        Object threshold,
        double contribution,
        String narrative) {
}
