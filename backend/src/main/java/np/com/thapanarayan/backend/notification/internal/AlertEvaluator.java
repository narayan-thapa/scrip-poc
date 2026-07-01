package np.com.thapanarayan.backend.notification.internal;

import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After signals are generated, matches them to watchlists/alert rules and dispatches notifications.
 * <b>Backfilled historical dates are skipped</b> (suppressNotifications) so a multi-year backfill
 * never spams users — only the latest/live run notifies.
 */
@Component
class AlertEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AlertEvaluator.class);

    private final NotificationService service;

    AlertEvaluator(NotificationService service) {
        this.service = service;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSignalsGenerated(SignalsGeneratedEvent e) {
        if (e.suppressNotifications()) {
            log.debug("Skipping notification fan-out for backfilled date {}", e.tradeDate());
            return;
        }
        service.generateForDate(e.tradeDate());
        service.pushUnsent();
    }
}
