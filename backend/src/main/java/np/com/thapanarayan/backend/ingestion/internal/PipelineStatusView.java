package np.com.thapanarayan.backend.ingestion.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A read-only snapshot of the daily pipeline (§10.11): the scheduler configuration,
 * the most recent ingestion batch and jobs (our domain view), and the last Spring
 * Batch job execution with per-step counters (the engine's view).
 */
record PipelineStatusView(
        boolean schedulerEnabled,
        String cron,
        String zone,
        BatchSummary lastBatch,
        List<JobSummary> recentJobs,
        BatchExecution lastBatchExecution) {

    record BatchSummary(long id, String status, LocalDate dateFrom, LocalDate dateTo,
            int fileCount, Instant finishedAt) {
    }

    record JobSummary(LocalDate tradeDate, String status, int rowsAccepted, int rowsRejected) {
    }

    record BatchExecution(String jobName, String status, String startTime, String endTime,
            List<StepSummary> steps) {
    }

    record StepSummary(String name, String status, long readCount, long writeCount, long skipCount) {
    }
}
