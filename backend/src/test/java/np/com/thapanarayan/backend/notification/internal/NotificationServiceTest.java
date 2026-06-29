package np.com.thapanarayan.backend.notification.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import np.com.thapanarayan.backend.notification.api.AlertType;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalQuery;
import np.com.thapanarayan.backend.signal.api.SignalView;
import np.com.thapanarayan.backend.watchlist.api.WatchlistQuery;

/** Alert-rule matching and per-(user, signal) dedup in the notifier. */
class NotificationServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);
    private static final UUID USER = UUID.randomUUID();

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final AlertRuleRepository alertRules = mock(AlertRuleRepository.class);
    private final SignalQuery signals = mock(SignalQuery.class);
    private final WatchlistQuery watchlists = mock(WatchlistQuery.class);
    private final RealtimeGateway gateway = mock(RealtimeGateway.class);

    private final NotificationService service = new NotificationService(
            notifications, alertRules, signals, watchlists, gateway,
            NepseClock.fixed(ZonedDateTime.now(NepseClock.NPT)));

    private static SignalView signal(String symbol, SignalAction action, double score) {
        return new SignalView(UUID.randomUUID(), symbol, DATE, action, BigDecimal.valueOf(score),
                60, List.of(), List.of(), action + " " + symbol, Instant.now());
    }

    private static AlertRuleEntity rule(AlertType type, String symbol, Map<String, Object> params) {
        AlertRuleEntity r = new AlertRuleEntity();
        r.setId(UUID.randomUUID());
        r.setUserId(USER);
        r.setType(type);
        r.setSymbol(symbol);
        r.setParams(params);
        r.setEnabled(true);
        r.setCreatedAt(Instant.now());
        return r;
    }

    @Test
    void createsNotificationWhenSignalActionRuleMatches() {
        when(signals.forDate(DATE)).thenReturn(List.of(signal("ABC", SignalAction.BUY, 50)));
        when(alertRules.findByEnabledTrue())
                .thenReturn(List.of(rule(AlertType.SIGNAL_ACTION, "ABC", Map.of("action", "BUY"))));
        when(notifications.existsByUserIdAndSignalId(any(), any())).thenReturn(false);
        when(notifications.findBySentFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        service.evaluateAndDispatch(DATE, 1);

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notifications).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getTitle()).contains("ABC");
        verify(gateway).broadcastSignals(DATE, 1);
    }

    @Test
    void doesNotMatchWhenActionDiffers() {
        when(signals.forDate(DATE)).thenReturn(List.of(signal("ABC", SignalAction.SELL, -50)));
        when(alertRules.findByEnabledTrue())
                .thenReturn(List.of(rule(AlertType.SIGNAL_ACTION, "ABC", Map.of("action", "BUY"))));
        when(notifications.findBySentFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        service.evaluateAndDispatch(DATE, 1);

        verify(notifications, never()).save(any());
    }

    @Test
    void dedupSkipsAnAlreadyNotifiedSignal() {
        when(signals.forDate(DATE)).thenReturn(List.of(signal("ABC", SignalAction.BUY, 80)));
        when(alertRules.findByEnabledTrue())
                .thenReturn(List.of(rule(AlertType.SCORE_THRESHOLD, "ABC", Map.of("minScore", 50))));
        when(notifications.existsByUserIdAndSignalId(eq(USER), any())).thenReturn(true); // already sent
        when(notifications.findBySentFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        service.evaluateAndDispatch(DATE, 1);

        verify(notifications, never()).save(any());
    }
}
