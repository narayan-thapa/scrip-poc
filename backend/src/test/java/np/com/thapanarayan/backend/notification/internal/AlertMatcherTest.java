package np.com.thapanarayan.backend.notification.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import np.com.thapanarayan.backend.notification.internal.domain.AlertRule;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalView;
import org.junit.jupiter.api.Test;

class AlertMatcherTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static SignalView sig(String symbol, SignalAction action) {
        return new SignalView(UUID.randomUUID().toString(), symbol, action, 42.0);
    }

    @Test
    void watchlistSymbolNotifiesOnBuy() {
        var candidates = AlertMatcher.match(
                Map.of("NABIL", sig("NABIL", SignalAction.BUY)),
                Map.of("NABIL", Set.of(USER)),
                List.of());
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).userId()).isEqualTo(USER);
        assertThat(candidates.get(0).title()).contains("BUY").contains("NABIL");
    }

    @Test
    void holdNeverNotifies() {
        var candidates = AlertMatcher.match(
                Map.of("NABIL", sig("NABIL", SignalAction.HOLD)),
                Map.of("NABIL", Set.of(USER)),
                List.of());
        assertThat(candidates).isEmpty();
    }

    @Test
    void signalActionRuleMatchesSymbolAndAction() {
        var rule = new AlertRule(OTHER, "SIGNAL_ACTION", Map.of("symbol", "NABIL", "action", "BUY"));
        var candidates = AlertMatcher.match(
                Map.of("NABIL", sig("NABIL", SignalAction.BUY)),
                Map.of(),
                List.of(rule));
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).userId()).isEqualTo(OTHER);
    }

    @Test
    void watchlistAndRuleForSameUserDeduped() {
        var rule = new AlertRule(USER, "SIGNAL_ACTION", Map.of("symbol", "NABIL", "action", "BUY"));
        var candidates = AlertMatcher.match(
                Map.of("NABIL", sig("NABIL", SignalAction.BUY)),
                Map.of("NABIL", Set.of(USER)),
                List.of(rule));
        assertThat(candidates).hasSize(1); // one notification per (user, signal)
    }
}
