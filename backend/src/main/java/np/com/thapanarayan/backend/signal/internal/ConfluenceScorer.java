package np.com.thapanarayan.backend.signal.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.StrategyVote;
import org.springframework.stereotype.Component;

/**
 * S9 — Confluence: blends weighted strategy votes into a score ∈ [-100, +100] and an action.
 * {@code score = 100 · Σ(wᵢ·voteᵢ·confᵢ) / Σ wᵢ}. Pure and deterministic; the full vote vector,
 * top contributing reasons, and dissenting strategies are retained for the "why" UI and audit.
 */
@Component
public class ConfluenceScorer {

    /** One strategy's evaluated vote with its configured weight. */
    public record Evaluated(String id, String name, double weight, StrategyVote vote) {}

    public ConfluenceResult score(List<Evaluated> evaluated, double buyThreshold, double sellThreshold) {
        double weightedSum = 0;
        double totalWeight = 0;
        List<ConfluenceResult.VoteEntry> entries = new ArrayList<>();
        List<Reason> reasons = new ArrayList<>();

        for (Evaluated e : evaluated) {
            double contribution = e.weight() * e.vote().vote() * e.vote().confidence();
            weightedSum += contribution;
            totalWeight += e.weight();
            entries.add(new ConfluenceResult.VoteEntry(e.id(), e.name(), e.vote().vote(),
                    e.vote().confidence(), e.weight(), contribution));
            reasons.addAll(e.vote().reasons());
        }

        double score = totalWeight == 0 ? 0 : 100.0 * weightedSum / totalWeight;
        SignalAction action = score >= buyThreshold ? SignalAction.BUY
                : score <= -sellThreshold ? SignalAction.SELL
                : SignalAction.HOLD;

        List<Reason> topReasons = reasons.stream()
                .sorted(Comparator.comparingDouble((Reason r) -> Math.abs(r.contribution())).reversed())
                .limit(5)
                .toList();

        List<String> dissents = entries.stream()
                .filter(v -> opposes(v.vote(), action))
                .map(ConfluenceResult.VoteEntry::strategyId)
                .toList();

        return new ConfluenceResult(round(score), action, Math.min(1.0, Math.abs(score) / 100.0),
                entries, topReasons, dissents);
    }

    private static boolean opposes(double vote, SignalAction action) {
        return (action == SignalAction.BUY && vote < 0) || (action == SignalAction.SELL && vote > 0);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
