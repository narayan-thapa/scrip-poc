package np.com.thapanarayan.backend.signal.internal;

import java.util.List;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalAction;

/** The blended outcome: score, action, the full per-strategy vote vector, top reasons and dissents. */
public record ConfluenceResult(
        double score,
        SignalAction action,
        double confidence,
        List<VoteEntry> votes,
        List<Reason> topReasons,
        List<String> dissents) {

    /** One strategy's contribution to the blend. */
    public record VoteEntry(String strategyId, String name, double vote, double confidence,
                            double weight, double contribution) {}
}
