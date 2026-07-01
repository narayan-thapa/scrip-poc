package np.com.thapanarayan.backend.signal.api;

import java.util.List;

/**
 * A strategy's directional vote: {@code vote ∈ [-1, +1]} (sign = direction, magnitude = conviction),
 * {@code confidence ∈ [0, 1]} (derived from distance-past-threshold / slope), and the reasons behind it.
 */
public record StrategyVote(double vote, double confidence, List<Reason> reasons) {

    public StrategyVote {
        vote = clamp(vote, -1, 1);
        confidence = clamp(confidence, 0, 1);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public static StrategyVote neutral() {
        return new StrategyVote(0, 0, List.of());
    }

    public static StrategyVote bullish(double confidence, Reason reason) {
        return new StrategyVote(1, confidence, List.of(reason));
    }

    public static StrategyVote bearish(double confidence, Reason reason) {
        return new StrategyVote(-1, confidence, List.of(reason));
    }

    public static StrategyVote graded(double vote, double confidence, Reason reason) {
        return new StrategyVote(vote, confidence, List.of(reason));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
