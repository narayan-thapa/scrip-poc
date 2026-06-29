package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;

/**
 * The durable daily cycle (§10.11): at 15:01 NPT — after the NEPSE close — launch the
 * ingestion pipeline for today's archived floorsheet, which cascades through market
 * data → indicators → signals via the AFTER_COMMIT event chain. Opt-in via
 * {@code nepse.orchestration.schedule-enabled} so it never fires unexpectedly in dev
 * or tests; a manual run is always available on the system endpoint.
 *
 * <p>Single-instance assumption: if deployed replicated, wrap the trigger with
 * ShedLock so only one node launches the job. Idempotent re-ingest (content-addressed
 * archive + {@code contract_id} upsert) makes a duplicate launch safe regardless.</p>
 */
@Component
class PipelineScheduler {

    private static final Logger log = LoggerFactory.getLogger(PipelineScheduler.class);

    private final IngestionService ingestion;
    private final NepseClock clock;
    private final OrchestrationProperties properties;

    PipelineScheduler(IngestionService ingestion, NepseClock clock, OrchestrationProperties properties) {
        this.ingestion = ingestion;
        this.clock = clock;
        this.properties = properties;
    }

    @Scheduled(cron = "${nepse.orchestration.cron:0 1 15 * * *}", zone = "Asia/Kathmandu")
    void runDailyPipeline() {
        if (!properties.scheduleEnabled()) {
            return;
        }
        LocalDate today = clock.today();
        log.info("Scheduled pipeline trigger for {}", today);
        try {
            ingestion.reprocessDate(today);
        } catch (NotFoundException noFileYet) {
            log.warn("No archived floorsheet for {} yet — scheduled run skipped", today);
        } catch (RuntimeException e) {
            log.error("Scheduled pipeline run failed for {}", today, e);
        }
    }
}
