package np.com.thapanarayan.backend.marketdata.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import np.com.thapanarayan.backend.ingestion.api.TradesIngestedEvent;

/**
 * Bridges ingestion → market-data aggregation. Runs AFTER_COMMIT of the ingestion
 * transaction so it only ever sees trades that are durably persisted. Aggregation
 * failures are logged, not rethrown, so one bad symbol/date never poisons the
 * event-dispatch thread; the date can be re-aggregated via the admin endpoint.
 */
@Component
class TradesIngestedListener {

    private static final Logger log = LoggerFactory.getLogger(TradesIngestedListener.class);

    private final MarketDataAggregationService aggregation;

    TradesIngestedListener(MarketDataAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTradesIngested(TradesIngestedEvent event) {
        try {
            aggregation.aggregate(event.tradeDate(), event.suppressNotifications());
        } catch (RuntimeException e) {
            log.error("Market-data aggregation failed for {}", event.tradeDate(), e);
        }
    }
}
