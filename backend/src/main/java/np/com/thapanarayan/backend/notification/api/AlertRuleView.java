package np.com.thapanarayan.backend.notification.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A user's alert rule.
 *
 * @param symbol the instrument it watches, or {@code null} for portfolio/watchlist-wide rules
 * @param params type-specific thresholds (e.g. {@code {"action":"BUY"}}, {@code {"minScore":50}})
 */
public record AlertRuleView(
        UUID id,
        AlertType type,
        String symbol,
        Map<String, Object> params,
        boolean enabled,
        Instant createdAt) {
}
