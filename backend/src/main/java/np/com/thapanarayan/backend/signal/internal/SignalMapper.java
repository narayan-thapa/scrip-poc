package np.com.thapanarayan.backend.signal.internal;

import java.util.Comparator;
import java.util.List;

import np.com.thapanarayan.backend.signal.api.ReasonView;
import np.com.thapanarayan.backend.signal.api.SignalView;
import np.com.thapanarayan.backend.signal.api.StrategyVoteView;

/** Entity → published-view conversion for signals. */
final class SignalMapper {

    private SignalMapper() {
    }

    /**
     * @param maxTopReasons how many highest-magnitude reasons to surface as the quick "why"
     */
    static SignalView toView(SignalEntity e, int maxTopReasons) {
        List<StrategyVoteView> votes = e.getVotes().stream().map(SignalMapper::voteView).toList();
        List<ReasonView> topReasons = votes.stream()
                .flatMap(v -> v.reasons().stream())
                .sorted(Comparator.comparingDouble((ReasonView r) -> Math.abs(r.contribution())).reversed())
                .limit(maxTopReasons)
                .toList();
        return new SignalView(
                e.getId(), e.getSymbol(), e.getTradeDate(), e.getAction(), e.getScore(),
                e.getBarCount(), votes, topReasons, e.getNarrative(), e.getComputedAt());
    }

    private static StrategyVoteView voteView(StoredVote v) {
        List<ReasonView> reasons = v.reasons().stream().map(SignalMapper::reasonView).toList();
        return new StrategyVoteView(
                v.strategyId(), v.label(), v.vote(), v.confidence(),
                v.weight(), v.contribution(), v.applicable(), reasons);
    }

    private static ReasonView reasonView(StoredReason r) {
        return new ReasonView(
                r.strategyId(), r.indicator(), r.condition(),
                r.observedValue(), r.threshold(), r.contribution(), r.narrative());
    }
}
