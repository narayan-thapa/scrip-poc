package np.com.thapanarayan.backend.signal.internal;

import java.util.List;

/**
 * A strategy's directional vote (§6.1). Ta4j supplies the boolean trigger; the
 * strategy maps it to a graded vote here.
 *
 * <p>Deliberate decomposition: {@code vote} ∈ {-1, 0, +1} is the <em>direction</em>
 * (from the boolean rule), {@code confidence} ∈ [0, 1] is the graded <em>strength</em>
 * (from distance-past-threshold / slope / trend filters). Their product is the
 * signed unit the confluence scorer blends — see {@link #signedContribution()}.</p>
 *
 * <p>{@code applicable} separates "ran, but no signal today" (a genuine neutral that
 * still dampens the blended score) from "could not run" (warm-up / missing
 * structure), which the scorer excludes from the denominator entirely.</p>
 *
 * @param vote       -1 bearish, 0 neutral, +1 bullish
 * @param confidence conviction in the vote, [0, 1]
 * @param applicable whether the strategy had enough data to produce a verdict
 * @param reasons    structured reasons explaining the vote (may be empty for neutral)
 */
record StrategyVote(double vote, double confidence, boolean applicable, List<Reason> reasons) {

    static final StrategyVote NEUTRAL = new StrategyVote(0.0, 0.0, true, List.of());
    static final StrategyVote NOT_APPLICABLE = new StrategyVote(0.0, 0.0, false, List.of());

    StrategyVote {
        vote = clamp(vote, -1.0, 1.0);
        confidence = clamp(confidence, 0.0, 1.0);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    static StrategyVote bullish(double confidence, List<Reason> reasons) {
        return new StrategyVote(1.0, confidence, true, reasons);
    }

    static StrategyVote bearish(double confidence, List<Reason> reasons) {
        return new StrategyVote(-1.0, confidence, true, reasons);
    }

    /** Ran, but found no actionable signal — still counts toward the blend's denominator. */
    static StrategyVote neutral() {
        return NEUTRAL;
    }

    /** Could not run (warm-up or missing structure) — excluded from the blend. */
    static StrategyVote notApplicable() {
        return NOT_APPLICABLE;
    }

    /** The signed unit blended by the confluence scorer: {@code vote * confidence} ∈ [-1, +1]. */
    double signedContribution() {
        return vote * confidence;
    }

    boolean isNeutral() {
        return vote == 0.0 || confidence == 0.0;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
