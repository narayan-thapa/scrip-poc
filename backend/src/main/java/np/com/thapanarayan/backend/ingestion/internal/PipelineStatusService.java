package np.com.thapanarayan.backend.ingestion.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.ingestion.internal.PipelineStatusView.BatchExecution;
import np.com.thapanarayan.backend.ingestion.internal.PipelineStatusView.BatchSummary;
import np.com.thapanarayan.backend.ingestion.internal.PipelineStatusView.JobSummary;
import np.com.thapanarayan.backend.ingestion.internal.PipelineStatusView.StepSummary;

/**
 * Assembles the pipeline-status snapshot (§10.11) from two sources: our ingestion
 * domain tables (latest batch + recent per-date jobs) and the Spring Batch
 * {@link JobRepository} (last {@code ingestionJob} execution and its step counters —
 * read/write/skip — which is the durable record that also enables restart inspection).
 */
@Service
class PipelineStatusService {

    private static final String JOB_NAME = "ingestionJob";
    private static final String ZONE = "Asia/Kathmandu";

    private final JobRepository jobRepository;
    private final IngestionBatchRepository batches;
    private final IngestionJobRepository jobs;
    private final OrchestrationProperties properties;

    PipelineStatusService(JobRepository jobRepository, IngestionBatchRepository batches,
            IngestionJobRepository jobs, OrchestrationProperties properties) {
        this.jobRepository = jobRepository;
        this.batches = batches;
        this.jobs = jobs;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public PipelineStatusView status() {
        BatchSummary lastBatch = batches.findFirstByOrderByIdDesc()
                .map(b -> new BatchSummary(b.getId(), b.getStatus().name(), b.getDateFrom(), b.getDateTo(),
                        b.getFileCount(), b.getFinishedAt()))
                .orElse(null);

        List<JobSummary> recentJobs = jobs.findAllByOrderByIdDesc(PageRequest.of(0, properties.recentJobs()))
                .map(j -> new JobSummary(j.getTradeDate(), j.getStatus().name(),
                        j.getRowsAccepted(), j.getRowsRejected()))
                .getContent();

        return new PipelineStatusView(
                properties.scheduleEnabled(), properties.cron(), ZONE,
                lastBatch, recentJobs, lastBatchExecution());
    }

    /** The most recent Spring Batch execution of the ingestion job, with step counters. */
    private BatchExecution lastBatchExecution() {
        List<JobInstance> instances = jobRepository.getJobInstances(JOB_NAME, 0, 1);
        if (instances.isEmpty()) {
            return null;
        }
        JobExecution execution = jobRepository.getLastJobExecution(instances.getFirst());
        if (execution == null) {
            return null;
        }
        List<StepSummary> steps = new ArrayList<>();
        for (StepExecution step : execution.getStepExecutions()) {
            steps.add(new StepSummary(step.getStepName(), step.getStatus().name(),
                    step.getReadCount(), step.getWriteCount(), step.getSkipCount()));
        }
        return new BatchExecution(JOB_NAME, execution.getStatus().name(),
                Objects.toString(execution.getStartTime(), null),
                Objects.toString(execution.getEndTime(), null), steps);
    }
}
