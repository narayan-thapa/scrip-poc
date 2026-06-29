package np.com.thapanarayan.backend.indicator.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import np.com.thapanarayan.backend.marketdata.api.MarketDataReadyEvent;

/**
 * Bridges market-data aggregation → indicator computation. Runs AFTER_COMMIT of
 * the aggregation transaction so candles are durably visible before snapshots are
 * built. Failures are logged, not rethrown, so one bad date never poisons the
 * event-dispatch thread; the date can be recomputed via the admin endpoint.
 */
@Component
class MarketDataReadyListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataReadyListener.class);

    private final IndicatorSnapshotService snapshots;

    MarketDataReadyListener(IndicatorSnapshotService snapshots) {
        this.snapshots = snapshots;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMarketDataReady(MarketDataReadyEvent event) {
        try {
            snapshots.computeForDate(event.tradeDate(), event.suppressNotifications());
        } catch (RuntimeException e) {
            log.error("Indicator computation failed for {}", event.tradeDate(), e);
        }
    }
}
