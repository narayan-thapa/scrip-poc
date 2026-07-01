package np.com.thapanarayan.backend.ingestion.internal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionBatch;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.domain.Statuses.BatchStatus;
import np.com.thapanarayan.backend.ingestion.internal.domain.Statuses.JobStatus;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionBatchRepository;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs a batch's per-date ingestion jobs in ascending date order (look-back windows for date N
 * depend on N-1, N-2, …). Each date is processed in its own transaction (via {@link IngestionService}),
 * so a crash leaves completed dates committed; on restart {@code IngestionRecoveryRunner} resumes the
 * remaining QUEUED/FAILED dates — idempotent thanks to the contract_id upsert.
 *
 * <p>Notifications are suppressed for every date except the batch's latest (most recent trading day),
 * so a historical backfill never spams users.
 */
@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final IngestionService ingestionService;
    private final IngestionBatchRepository batches;
    private final IngestionJobRepository jobs;

    IngestionPipeline(IngestionService ingestionService, IngestionBatchRepository batches,
                      IngestionJobRepository jobs) {
        this.ingestionService = ingestionService;
        this.batches = batches;
        this.jobs = jobs;
    }

    @Async
    public void runBatchAsync(UUID batchId) {
        runBatch(batchId);
    }

    /** Process (or resume) all not-yet-completed jobs of a batch, oldest date first. */
    public void runBatch(UUID batchId) {
        IngestionBatch batch = batches.findById(batchId).orElseThrow();
        batch.setStatus(BatchStatus.RUNNING);
        batches.save(batch);

        List<IngestionJob> pending = jobs.findByBatchIdAndStatusInOrderByTradeDateAsc(
                batchId, List.of(JobStatus.QUEUED, JobStatus.FAILED));
        boolean anyFailed = false;

        for (IngestionJob job : pending) {
            // Only the most recent trading day in the batch notifies users.
            boolean suppress = batch.getDateTo() == null || !job.getTradeDate().equals(batch.getDateTo());
            try {
                ingestionService.processJob(job.getId(), suppress);
            } catch (RuntimeException e) {
                anyFailed = true;
                log.warn("Date {} failed in batch {}; continuing with remaining dates", job.getTradeDate(), batchId);
            }
        }

        boolean stillIncomplete = jobs.findByBatchIdAndStatusInOrderByTradeDateAsc(
                batchId, List.of(JobStatus.QUEUED, JobStatus.FAILED, JobStatus.RUNNING)).isEmpty() == false;
        batch.setStatus(stillIncomplete || anyFailed ? BatchStatus.PARTIAL : BatchStatus.COMPLETED);
        batch.setFinishedAt(OffsetDateTime.now());
        batches.save(batch);
    }
}
