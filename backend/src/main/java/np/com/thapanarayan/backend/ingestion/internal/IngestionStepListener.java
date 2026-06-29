package np.com.thapanarayan.backend.ingestion.internal;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

import np.com.thapanarayan.backend.ingestion.api.IngestionStatus;
import np.com.thapanarayan.backend.platform.api.NepseClock;

/**
 * Records the outcome of an ingest step: quarantines skipped rows into
 * {@code ingestion_rejection}, counts rejections vs in-file duplicates, and on
 * step completion writes the read/accepted/rejected/duplicate tallies and final
 * status onto the {@code ingestion_job} row. Step-scoped (one per file execution).
 */
class IngestionStepListener implements SkipListener<String, ParsedTrade>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(IngestionStepListener.class);

    private final long jobId;
    private final IngestionRejectionRepository rejections;
    private final IngestionJobRepository jobs;
    private final NepseClock clock;

    private final AtomicInteger rejected = new AtomicInteger();
    private final AtomicInteger duplicates = new AtomicInteger();

    IngestionStepListener(long jobId, IngestionRejectionRepository rejections,
            IngestionJobRepository jobs, NepseClock clock) {
        this.jobId = jobId;
        this.rejections = rejections;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setStatus(IngestionStatus.RUNNING);
            job.setStartedAt(Instant.now(clock.clock()));
            jobs.save(job);
        });
    }

    @Override
    public void onSkipInProcess(String item, Throwable t) {
        if (t instanceof DuplicateRowException) {
            duplicates.incrementAndGet();
            return;
        }
        if (t instanceof RowRejectedException rre) {
            quarantine(item, rre.reason().name(), rre.getMessage());
        } else {
            quarantine(item, "UNEXPECTED", t.getMessage());
        }
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skip during read on job {}: {}", jobId, t.getMessage());
    }

    @Override
    public void onSkipInWrite(ParsedTrade item, Throwable t) {
        quarantine(item.contractId(), "WRITE_ERROR", t.getMessage());
    }

    private void quarantine(String rawLine, String reasonCode, String detail) {
        rejected.incrementAndGet();
        rejections.save(new IngestionRejectionEntity(jobId, rawLine, reasonCode, detail));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setRowsRead((int) stepExecution.getReadCount());
            job.setRowsAccepted((int) stepExecution.getWriteCount());
            job.setRowsRejected(rejected.get());
            job.setRowsDuplicate(duplicates.get());
            job.setStatus(stepExecution.getStatus() == BatchStatus.COMPLETED
                    ? IngestionStatus.COMPLETED : IngestionStatus.FAILED);
            job.setFinishedAt(Instant.now(clock.clock()));
            jobs.save(job);
        });
        return stepExecution.getExitStatus();
    }
}
