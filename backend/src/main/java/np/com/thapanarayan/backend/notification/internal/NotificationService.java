package np.com.thapanarayan.backend.notification.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.notification.api.NotificationView;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.platform.api.PageResponse;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalQuery;
import np.com.thapanarayan.backend.signal.api.SignalView;
import np.com.thapanarayan.backend.watchlist.api.WatchlistQuery;

/**
 * Evaluates alert rules against a day's signals, persists deduped notifications
 * (outbox), dispatches the unsent ones over realtime, and serves the feed (§10.9).
 */
@Service
class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final AlertRuleRepository alertRules;
    private final SignalQuery signals;
    private final WatchlistQuery watchlists;
    private final RealtimeGateway gateway;
    private final NepseClock clock;

    NotificationService(NotificationRepository notifications, AlertRuleRepository alertRules,
            SignalQuery signals, WatchlistQuery watchlists, RealtimeGateway gateway, NepseClock clock) {
        this.notifications = notifications;
        this.alertRules = alertRules;
        this.signals = signals;
        this.watchlists = watchlists;
        this.gateway = gateway;
        this.clock = clock;
    }

    /** Evaluate rules for {@code tradeDate}, persist+dispatch notifications, and broadcast the run. */
    @Transactional
    public void evaluateAndDispatch(LocalDate tradeDate, int signalsGenerated) {
        List<SignalView> daySignals = signals.forDate(tradeDate);
        if (!daySignals.isEmpty()) {
            Map<String, SignalView> bySymbol = new LinkedHashMap<>();
            daySignals.forEach(s -> bySymbol.put(s.symbol(), s));
            for (AlertRuleEntity rule : alertRules.findByEnabledTrue()) {
                evaluateRule(rule, bySymbol, daySignals);
            }
        }
        dispatchUnsent();
        gateway.broadcastSignals(tradeDate, signalsGenerated);
    }

    private void evaluateRule(AlertRuleEntity rule, Map<String, SignalView> bySymbol, List<SignalView> all) {
        switch (rule.getType()) {
            case SIGNAL_ACTION -> {
                SignalView s = bySymbol.get(rule.getSymbol());
                String wanted = str(rule.getParams().get("action"));
                if (s != null && wanted != null && s.action().name().equalsIgnoreCase(wanted)) {
                    create(rule.getUserId(), s);
                }
            }
            case SCORE_THRESHOLD -> {
                SignalView s = bySymbol.get(rule.getSymbol());
                Double minScore = num(rule.getParams().get("minScore"));
                if (s != null && minScore != null && Math.abs(s.score().doubleValue()) >= minScore) {
                    create(rule.getUserId(), s);
                }
            }
            case WATCHLIST_SIGNAL -> {
                Set<String> watched = watchlists.symbolsForUser(rule.getUserId());
                all.stream()
                        .filter(s -> watched.contains(s.symbol()) && s.action() != SignalAction.HOLD)
                        .forEach(s -> create(rule.getUserId(), s));
            }
        }
    }

    /** Persist one notification, deduped per (user, signal). */
    private void create(UUID userId, SignalView signal) {
        if (notifications.existsByUserIdAndSignalId(userId, signal.id())) {
            return;
        }
        NotificationEntity e = new NotificationEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setSignalId(signal.id());
        e.setTitle("%s signal: %s".formatted(signal.action(), signal.symbol()));
        e.setBody(signal.narrative());
        e.setRead(false);
        e.setSent(false);
        e.setCreatedAt(Instant.now(clock.clock()));
        notifications.save(e);
    }

    private void dispatchUnsent() {
        for (NotificationEntity e : notifications.findBySentFalseOrderByCreatedAtAsc()) {
            try {
                gateway.sendNotification(e.getUserId(), toView(e));
                e.setSent(true); // flushed by the surrounding transaction
            } catch (RuntimeException pushFailed) {
                log.warn("Notification {} stays queued (push failed): {}", e.getId(), pushFailed.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationView> list(UUID userId, int page, int size) {
        return PageResponse.from(notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(NotificationService::toView));
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationView markRead(UUID userId, UUID id) {
        NotificationEntity e = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        e.setRead(true);
        return toView(e);
    }

    private static NotificationView toView(NotificationEntity e) {
        return new NotificationView(e.getId(), e.getSignalId(), e.getTitle(), e.getBody(),
                e.isRead(), e.getCreatedAt());
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Double num(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }
}
