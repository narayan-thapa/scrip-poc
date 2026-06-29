package np.com.thapanarayan.backend.signal.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import np.com.thapanarayan.backend.indicator.api.IndicatorsComputedEvent;

/**
 * Bridges indicator computation → signal generation. Runs AFTER_COMMIT of the
 * indicator transaction so snapshots/candles are durably visible before strategies
 * read them. Failures are logged, not rethrown, so one bad date never poisons the
 * event-dispatch thread; the date can be regenerated via the admin endpoint.
 */
@Component
class IndicatorsComputedListener {

    private static final Logger log = LoggerFactory.getLogger(IndicatorsComputedListener.class);

    private final SignalGenerationService generation;

    IndicatorsComputedListener(SignalGenerationService generation) {
        this.generation = generation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onIndicatorsComputed(IndicatorsComputedEvent event) {
        try {
            generation.generateForDate(event.tradeDate(), event.suppressNotifications());
        } catch (RuntimeException e) {
            log.error("Signal generation failed for {}", event.tradeDate(), e);
        }
    }
}
