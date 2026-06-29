package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.StringJoiner;

import np.com.thapanarayan.backend.signal.api.SignalAction;

/**
 * Composes the one-line human-readable summary stored on each signal, e.g.
 * "BUY ABC (score +42.0): 4 of 7 strategies bullish (Trend following, MACD, …);
 * Mean reversion dissenting." Built from the scored votes so it always agrees with
 * the breakdown.
 */
final class NarrativeBuilder {

    private NarrativeBuilder() {
    }

    static String build(String symbol, SignalAction action, BigDecimal score, List<ScoredVote> votes) {
        long applicable = votes.stream().filter(ScoredVote::applicable).count();
        long bullish = votes.stream().filter(v -> v.applicable() && v.vote().vote() > 0).count();
        long bearish = votes.stream().filter(v -> v.applicable() && v.vote().vote() < 0).count();

        StringBuilder sb = new StringBuilder();
        sb.append("%s %s (score %+.1f): ".formatted(action, symbol, score.doubleValue()));

        if (applicable == 0) {
            sb.append("no strategy had enough data to vote.");
            return sb.toString();
        }

        long majority = action == SignalAction.SELL ? bearish : bullish;
        String direction = action == SignalAction.SELL ? "bearish" : "bullish";
        sb.append("%d of %d strategies %s".formatted(majority, applicable, direction));

        String supporters = names(votes, action == SignalAction.SELL ? -1 : 1);
        if (!supporters.isEmpty()) {
            sb.append(" (").append(supporters).append(")");
        }

        String dissenters = names(votes, action == SignalAction.SELL ? 1 : -1);
        if (action != SignalAction.HOLD && !dissenters.isEmpty()) {
            sb.append("; ").append(dissenters).append(" dissenting");
        }
        sb.append(".");
        return sb.toString();
    }

    /** Comma-joined labels of applicable votes whose direction matches {@code sign}. */
    private static String names(List<ScoredVote> votes, int sign) {
        StringJoiner joiner = new StringJoiner(", ");
        for (ScoredVote v : votes) {
            if (v.applicable() && Math.signum(v.vote().vote()) == sign) {
                joiner.add(v.label());
            }
        }
        return joiner.toString();
    }
}
