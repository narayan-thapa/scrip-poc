package np.com.thapanarayan.backend.notification.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.notification.internal.domain.Notification;
import np.com.thapanarayan.backend.notification.internal.domain.NotificationRepository;
import np.com.thapanarayan.backend.notification.internal.domain.AlertRuleRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.page.PageResponse;
import np.com.thapanarayan.backend.signal.api.SignalReader;
import np.com.thapanarayan.backend.watchlist.api.WatchlistReader;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Creates notifications from the day's signals (persisted in the same step → durable), pushes unsent
 * ones over SSE and flips the sent flag (a small outbox), and serves the user feed. A crash between
 * persist and push is recovered on the next {@code pushUnsent()} (startup + after each run).
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final AlertRuleRepository rules;
    private final SignalReader signals;
    private final WatchlistReader watchlists;
    private final SseHub sse;

    NotificationService(NotificationRepository notifications, AlertRuleRepository rules, SignalReader signals,
                        WatchlistReader watchlists, SseHub sse) {
        this.notifications = notifications;
        this.rules = rules;
        this.signals = signals;
        this.watchlists = watchlists;
        this.sse = sse;
    }

    /** Match the date's signals against watchlists + rules and persist deduped notifications. */
    @Transactional
    public void generateForDate(LocalDate date) {
        List<AlertMatcher.Candidate> candidates = AlertMatcher.match(
                signals.byDate(date), watchlists.watchersBySymbol(), rules.findByEnabledTrue());
        for (AlertMatcher.Candidate c : candidates) {
            if (!notifications.existsByUserIdAndSignalId(c.userId(), c.signalId())) {
                notifications.save(new Notification(c.userId(), c.signalId(), c.symbol(), c.title(), c.body()));
            }
        }
    }

    /** Push every unsent notification over SSE and mark it sent (crash-safe outbox). */
    @Transactional
    public void pushUnsent() {
        for (Notification n : notifications.findBySentFalse()) {
            sse.send(n.getUserId(), "notification", NotificationDto.from(n));
            n.markSent();
            notifications.save(n);
        }
    }

    public PageResponse<NotificationDto> feed(UUID userId, Pageable pageable) {
        return PageResponse.from(notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable), NotificationDto::from);
    }

    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadFlagFalse(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        Notification n = notifications.findById(id)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Unknown notification: " + id));
        n.markRead();
        notifications.save(n);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId);
    }

    public SseEmitter subscribe(UUID userId) {
        return sse.subscribe(userId);
    }
}
