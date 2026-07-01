package np.com.thapanarayan.backend.ingestion.internal;

import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.time.NepalClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily 15:01 NPT trigger (the degenerate one-date case of the backfill pipeline). Re-runs ingestion
 * for today from the archived raw file, driving the aggregate → indicators → signals → notify chain.
 * If the scraper hasn't delivered today's file yet, it no-ops gracefully. With multiple replicas this
 * would be wrapped in ShedLock; single-instance needs no lock.
 */
@Component
@ConditionalOnProperty(prefix = "ingestion.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final ReprocessService reprocess;
    private final NepalClock clock;

    IngestionScheduler(ReprocessService reprocess, NepalClock clock) {
        this.reprocess = reprocess;
        this.clock = clock;
    }

    @Scheduled(cron = "0 1 15 * * *", zone = "Asia/Kathmandu")
    void dailyRun() {
        var today = clock.today();
        log.info("15:01 NPT scheduled pipeline run for {}", today);
        try {
            reprocess.reprocess(today, today);
        } catch (ApiException e) {
            log.info("No archived file for {} yet — skipping (webhook/upload will trigger it): {}",
                    today, e.getMessage());
        }
    }
}
