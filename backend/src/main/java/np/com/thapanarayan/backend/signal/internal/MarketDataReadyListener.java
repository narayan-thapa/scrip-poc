package np.com.thapanarayan.backend.signal.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import np.com.thapanarayan.backend.marketdata.api.MarketDataReadyEvent;

/**
 * Bridges market-data aggregation → signal generation. Signals are derived from the
 * candle {@code BarSeries} (each strategy recomputes its own indicators) plus the
 * volume-profile and broker-flow structures — all market-data outputs — so this
 * stage does <em>not</em> depend on the persisted indicator snapshots. It therefore
 * triggers directly off {@link MarketDataReadyEvent} (AFTER_COMMIT, so candles are
 * durably visible) and runs {@code @Async} on the pipeline executor, concurrently
 * with the indicator stage rather than waiting for it.
 *
 * <p>Failures are logged, not rethrown, so one bad date never poisons the executor
 * thread; the date can be regenerated via the admin endpoint.</p>
 */
@Component
class MarketDataReadyListener {

    private static final Logger log = LoggerFactory.getLogger(MarketDataReadyListener.class);

    private final SignalGenerationService generation;

    MarketDataReadyListener(SignalGenerationService generation) {
        this.generation = generation;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onMarketDataReady(MarketDataReadyEvent event) {
        try {
            generation.generateForDate(event.tradeDate(), event.suppressNotifications());
        } catch (RuntimeException e) {
            log.error("Signal generation failed for {}", event.tradeDate(), e);
        }
    }
}
