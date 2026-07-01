package np.com.thapanarayan.backend.ingestion.internal;

import java.util.List;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionBatch;
import np.com.thapanarayan.backend.ingestion.internal.domain.Statuses.BatchStatus;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Crash recovery: on startup, resume any batch left QUEUED or RUNNING (a JVM died mid-ingest). The
 * pipeline re-runs only the incomplete dates and the contract_id upsert makes re-ingestion idempotent,
 * so processing resumes from the failed date without duplicating committed rows.
 */
@Component
@Order(100)
class IngestionRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRecoveryRunner.class);

    private final IngestionBatchRepository batches;
    private final IngestionPipeline pipeline;

    IngestionRecoveryRunner(IngestionBatchRepository batches, IngestionPipeline pipeline) {
        this.batches = batches;
        this.pipeline = pipeline;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<IngestionBatch> incomplete = batches.findAll().stream()
                .filter(b -> b.getStatus() == BatchStatus.QUEUED || b.getStatus() == BatchStatus.RUNNING)
                .toList();
        for (IngestionBatch batch : incomplete) {
            log.info("Resuming incomplete ingestion batch {} (status {})", batch.getId(), batch.getStatus());
            pipeline.runBatchAsync(batch.getId());
        }
    }
}
