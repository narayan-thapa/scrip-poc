package np.com.thapanarayan.backend.notification.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;

/**
 * Drives the notifier off the pipeline's terminal event (§10.9): on
 * {@code SignalsGeneratedEvent}, evaluate alert rules → persist+push notifications →
 * broadcast the run. Runs AFTER_COMMIT so signals are durably queryable; failures are
 * logged, never rethrown. Skipped for historical backfills ({@code suppressNotifications}).
 */
@Component
class SignalsGeneratedListener {

    private static final Logger log = LoggerFactory.getLogger(SignalsGeneratedListener.class);

    private final NotificationService notifications;

    SignalsGeneratedListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSignalsGenerated(SignalsGeneratedEvent event) {
        if (event.suppressNotifications()) {
            return;
        }
        try {
            notifications.evaluateAndDispatch(event.tradeDate(), event.signalsCreated());
        } catch (RuntimeException e) {
            log.error("Notification evaluation failed for {}", event.tradeDate(), e);
        }
    }
}
