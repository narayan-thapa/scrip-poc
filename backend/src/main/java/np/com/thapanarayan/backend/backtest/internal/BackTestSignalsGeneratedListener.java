package np.com.thapanarayan.backend.backtest.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;

/**
 * Auto-refreshes strategy validation when a new day's signals land (§10.7): on
 * {@code SignalsGeneratedEvent}, backtests the day's BUY list over a trailing window
 * so the UI can show how the confluence model has performed on exactly the names it
 * is now flagging. Runs AFTER_COMMIT; failures are logged, never rethrown.
 *
 * <p>Skipped for historical backfills ({@code suppressNotifications}) and empty BUY
 * lists, to avoid redundant cost during bulk reprocessing.</p>
 */
@Component
class BackTestSignalsGeneratedListener {

    private static final Logger log = LoggerFactory.getLogger(BackTestSignalsGeneratedListener.class);

    private final BacktestService service;
    private final BacktestProperties properties;

    BackTestSignalsGeneratedListener(BacktestService service, BacktestProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSignalsGenerated(SignalsGeneratedEvent event) {
        if (event.suppressNotifications() || event.buySymbols().isEmpty()) {
            return;
        }
        try {
            BacktestRequest request = new BacktestRequest(
                    event.buySymbols(),
                    event.tradeDate().minusDays(properties.refreshWindowDays()),
                    event.tradeDate(),
                    null, null);
            service.run(request);
        } catch (RuntimeException e) {
            log.error("Backtest auto-refresh failed for {}", event.tradeDate(), e);
        }
    }
}
