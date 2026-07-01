package np.com.thapanarayan.backend.screener.internal;

import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pre-warms the day dashboard and the 30/45/60 price-drop presets after signals are generated, so
 * those pages load instantly from cache. Custom windows are computed on demand. Runs after commit so
 * the signals it annotates rows with are visible.
 */
@Component
class ScreenerWarmup {

    private static final Logger log = LoggerFactory.getLogger(ScreenerWarmup.class);

    private final ScreenerService service;
    private final ScreenerProperties props;

    ScreenerWarmup(ScreenerService service, ScreenerProperties props) {
        this.service = service;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSignalsGenerated(SignalsGeneratedEvent e) {
        service.evict(e.tradeDate());
        service.dayDashboard(e.tradeDate(), props.baselineWindow());
        for (int window : props.dropPresets()) {
            service.priceDropCached(e.tradeDate(), window, "pctchange", null, 50);
        }
        log.info("Pre-warmed screener caches for {}", e.tradeDate());
    }
}
