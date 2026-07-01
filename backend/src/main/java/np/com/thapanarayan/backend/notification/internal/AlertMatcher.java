package np.com.thapanarayan.backend.notification.internal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import np.com.thapanarayan.backend.notification.internal.domain.AlertRule;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalView;

/**
 * Pure matching of the day's signals against watchlists + user alert rules → notification candidates.
 * HOLD signals never notify. Deduplicated per (user, signal). Kept side-effect free for unit testing.
 */
final class AlertMatcher {

    private AlertMatcher() {
    }

    record Candidate(UUID userId, UUID signalId, String title, String body) {}

    static List<Candidate> match(Map<String, SignalView> signals,
                                 Map<String, Set<UUID>> watchersBySymbol,
                                 List<AlertRule> enabledRules) {
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(); // userId:signalId

        for (SignalView sv : signals.values()) {
            if (sv.action() == SignalAction.HOLD) {
                continue;
            }
            UUID signalId = UUID.fromString(sv.id());
            String title = sv.action() + " · " + sv.symbol();
            String body = "Confluence score " + fmt(sv.score());

            // (a) watchlist-driven: anyone watching this symbol.
            for (UUID userId : watchersBySymbol.getOrDefault(sv.symbol(), Set.of())) {
                add(out, seen, userId, signalId, title, body);
            }
            // (b) rule-driven: SIGNAL_ACTION rules matching this symbol+action.
            for (AlertRule rule : enabledRules) {
                if ("SIGNAL_ACTION".equals(rule.getType())
                        && sv.symbol().equalsIgnoreCase(str(rule.getParams().get("symbol")))
                        && sv.action().name().equalsIgnoreCase(str(rule.getParams().get("action")))) {
                    add(out, seen, rule.getUserId(), signalId, title, body);
                }
            }
        }
        return out;
    }

    private static void add(List<Candidate> out, Set<String> seen, UUID userId, UUID signalId,
                            String title, String body) {
        if (seen.add(userId + ":" + signalId)) {
            out.add(new Candidate(userId, signalId, title, body));
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }
}
