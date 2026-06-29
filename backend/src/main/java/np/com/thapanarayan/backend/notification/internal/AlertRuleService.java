package np.com.thapanarayan.backend.notification.internal;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.notification.api.AlertRuleView;
import np.com.thapanarayan.backend.notification.api.AlertType;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;

/** Alert-rule CRUD (§10.10), scoped to the owning user. The Stage 9 notifier consumes these. */
@Service
class AlertRuleService {

    private final AlertRuleRepository rules;
    private final NepseClock clock;

    AlertRuleService(AlertRuleRepository rules, NepseClock clock) {
        this.rules = rules;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleView> list(UUID userId) {
        return rules.findByUserIdOrderByCreatedAtDesc(userId).stream().map(AlertRuleService::toView).toList();
    }

    @Transactional(readOnly = true)
    public AlertRuleView get(UUID userId, UUID id) {
        return toView(owned(userId, id));
    }

    @Transactional
    public AlertRuleView create(UUID userId, AlertType type, String symbol, Map<String, Object> params) {
        if (type == null) {
            throw new DomainException("INVALID_ALERT", "Alert type is required");
        }
        AlertRuleEntity e = new AlertRuleEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setType(type);
        e.setSymbol(normalizeSymbol(symbol));
        e.setParams(params == null ? Map.of() : params);
        e.setEnabled(true);
        e.setCreatedAt(Instant.now(clock.clock()));
        return toView(rules.save(e));
    }

    @Transactional
    public AlertRuleView update(UUID userId, UUID id, boolean enabled, String symbol, Map<String, Object> params) {
        AlertRuleEntity e = owned(userId, id);
        e.setEnabled(enabled);
        e.setSymbol(normalizeSymbol(symbol));
        if (params != null) {
            e.setParams(params);
        }
        return toView(e);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        rules.delete(owned(userId, id));
    }

    private AlertRuleEntity owned(UUID userId, UUID id) {
        return rules.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Alert rule not found"));
    }

    private static AlertRuleView toView(AlertRuleEntity e) {
        return new AlertRuleView(e.getId(), e.getType(), e.getSymbol(), e.getParams(), e.isEnabled(), e.getCreatedAt());
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
