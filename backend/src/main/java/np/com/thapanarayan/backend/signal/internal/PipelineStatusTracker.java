package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import np.com.thapanarayan.backend.indicator.api.IndicatorsComputedEvent;
import np.com.thapanarayan.backend.ingestion.api.TradesIngestedEvent;
import np.com.thapanarayan.backend.marketdata.api.MarketDataReadyEvent;
import np.com.thapanarayan.backend.signal.api.SignalsGeneratedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tracks the last date each pipeline stage completed (in-memory), updated by the stage events. Backs
 * {@code /system/pipeline/status}. Lives in the terminal signal module, which already observes the
 * upstream stage events via their published api packages.
 */
@Component
public class PipelineStatusTracker {

    /** One stage's latest progress. */
    public record StageStatus(String stage, String lastDate, String updatedAt) {}

    private final Map<String, StageStatus> stages = new ConcurrentHashMap<>();

    @EventListener
    void onIngested(TradesIngestedEvent e) {
        record("ingest", e.tradeDate());
    }

    @EventListener
    void onMarketData(MarketDataReadyEvent e) {
        record("aggregate", e.tradeDate());
    }

    @EventListener
    void onIndicators(IndicatorsComputedEvent e) {
        record("indicators", e.tradeDate());
    }

    @EventListener
    void onSignals(SignalsGeneratedEvent e) {
        record("signals", e.tradeDate());
    }

    private void record(String stage, LocalDate date) {
        stages.put(stage, new StageStatus(stage, date.toString(), OffsetDateTime.now().toString()));
    }

    /** Stages in pipeline order; missing stages report as not-yet-run. */
    public List<StageStatus> snapshot() {
        return List.of("ingest", "aggregate", "indicators", "signals").stream()
                .map(s -> stages.getOrDefault(s, new StageStatus(s, null, null)))
                .toList();
    }
}
