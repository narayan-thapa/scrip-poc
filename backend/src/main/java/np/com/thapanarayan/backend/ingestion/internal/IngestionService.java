package np.com.thapanarayan.backend.ingestion.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import np.com.thapanarayan.backend.ingestion.api.BatchSubmissionResult;
import np.com.thapanarayan.backend.ingestion.api.BatchSubmissionResult.FileIntake;
import np.com.thapanarayan.backend.ingestion.api.IngestionJobView;
import np.com.thapanarayan.backend.ingestion.api.IngestionStatus;
import np.com.thapanarayan.backend.ingestion.api.TradesIngestedEvent;
import np.com.thapanarayan.backend.ingestion.internal.RawFileArchive.ArchivedFile;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.DomainEventPublisher;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.platform.api.TradingCalendar;

/**
 * Orchestrates ingestion: filename/size validation, content-addressed archival,
 * per-file/per-batch job records, and asynchronous launch of the Spring Batch
 * ingest job (dates ascending for backfills). Repository writes are individually
 * transactional and committed before the async task runs, so the launched job
 * always sees its row. Publishes {@link TradesIngestedEvent} after each date.
 */
@Service
class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final RawFileArchive archive;
    private final IngestionJobRepository jobs;
    private final IngestionBatchRepository batches;
    private final IngestionJobFactory jobFactory;
    private final JobLauncher jobLauncher;
    private final ExecutorService executor;
    private final DomainEventPublisher events;
    private final TradingCalendar calendar;
    private final NepseClock clock;
    private final IngestionProperties properties;

    IngestionService(RawFileArchive archive, IngestionJobRepository jobs,
            IngestionBatchRepository batches, IngestionJobFactory jobFactory, JobLauncher jobLauncher,
            ExecutorService ingestionExecutor, DomainEventPublisher events, TradingCalendar calendar,
            NepseClock clock, IngestionProperties properties) {
        this.archive = archive;
        this.jobs = jobs;
        this.batches = batches;
        this.jobFactory = jobFactory;
        this.jobLauncher = jobLauncher;
        this.executor = ingestionExecutor;
        this.events = events;
        this.calendar = calendar;
        this.clock = clock;
        this.properties = properties;
    }

    private record JobLaunch(long jobId, Path path, LocalDate date) {
    }

    // ---- Single upload -------------------------------------------------------

    IngestionJobView submitSingle(MultipartFile file) {
        LocalDate date = FilenameValidator.requireValidAndExtractDate(file.getOriginalFilename());
        requireSizeWithinLimit(file.getSize());
        checkTradingDay(date);
        ArchivedFile archived = archiveOrThrow(date, file);
        IngestionJobEntity job = createJob(null, date, file.getOriginalFilename(), archived.sha256());
        executor.submit(() -> runJob(new JobLaunch(job.getId(), archived.path(), date), false));
        return IngestionMapper.toView(job);
    }

    // ---- Batch upload / backfill --------------------------------------------

    BatchSubmissionResult submitBatch(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new DomainException("EMPTY_BATCH", "no files supplied");
        }
        if (files.size() > properties.maxBatchFiles()) {
            throw new DomainException("TOO_MANY_FILES",
                    "batch exceeds " + properties.maxBatchFiles() + " files");
        }
        long totalBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > properties.maxBatchBytes()) {
            throw new DomainException("BATCH_TOO_LARGE", "batch exceeds the total-size cap");
        }

        // Validate + dedup by date, preserving original order for the response.
        TreeMap<LocalDate, MultipartFile> accepted = new TreeMap<>();
        List<FileIntake> intakes = new ArrayList<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            try {
                LocalDate date = FilenameValidator.requireValidAndExtractDate(name);
                requireSizeWithinLimit(file.getSize());
                if (accepted.containsKey(date)) {
                    intakes.add(new FileIntake(name, date, false, "duplicate date within batch"));
                } else {
                    accepted.put(date, file);
                    intakes.add(new FileIntake(name, date, true, "queued"));
                }
            } catch (DomainException e) {
                intakes.add(new FileIntake(name, null, false, e.getMessage()));
            }
        }
        if (accepted.isEmpty()) {
            throw new DomainException("NO_VALID_FILES", "no files passed intake validation");
        }

        IngestionBatchEntity batch = createBatch(accepted.size(), accepted.firstKey(), accepted.lastKey());

        List<JobLaunch> launches = new ArrayList<>();
        for (var entry : accepted.entrySet()) {
            LocalDate date = entry.getKey();
            MultipartFile file = entry.getValue();
            ArchivedFile archived = archiveOrThrow(date, file);
            IngestionJobEntity job = createJob(batch.getId(), date, file.getOriginalFilename(), archived.sha256());
            launches.add(new JobLaunch(job.getId(), archived.path(), date));
        }

        LocalDate latest = accepted.lastKey();
        executor.submit(() -> runBatch(batch.getId(), launches, latest));
        return new BatchSubmissionResult(batch.getId(), intakes);
    }

    // ---- Trigger / reprocess from the archive --------------------------------

    IngestionJobView reprocessDate(LocalDate date) {
        ArchivedFile archived = archive.findLatest(date)
                .orElseThrow(() -> new NotFoundException("No archived raw file for " + date));
        IngestionJobEntity job = createJob(null, date, date + ".csv", archived.sha256());
        executor.submit(() -> runJob(new JobLaunch(job.getId(), archived.path(), date), false));
        return IngestionMapper.toView(job);
    }

    BatchSubmissionResult reprocessRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new DomainException("BAD_RANGE", "'from' must not be after 'to'");
        }
        TreeMap<LocalDate, ArchivedFile> found = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            archive.findLatest(d).ifPresent(a -> found.put(a.tradeDate(), a));
        }
        if (found.isEmpty()) {
            throw new NotFoundException("No archived raw files in range " + from + ".." + to);
        }
        IngestionBatchEntity batch = createBatch(found.size(), found.firstKey(), found.lastKey());
        List<JobLaunch> launches = new ArrayList<>();
        List<FileIntake> intakes = new ArrayList<>();
        for (var entry : found.entrySet()) {
            LocalDate date = entry.getKey();
            IngestionJobEntity job = createJob(batch.getId(), date, date + ".csv", entry.getValue().sha256());
            launches.add(new JobLaunch(job.getId(), entry.getValue().path(), date));
            intakes.add(new FileIntake(date + ".csv", date, true, "queued"));
        }
        LocalDate latest = found.lastKey();
        executor.submit(() -> runBatch(batch.getId(), launches, latest));
        return new BatchSubmissionResult(batch.getId(), intakes);
    }

    IngestionJobView triggerFromWebhook(LocalDate date) {
        return reprocessDate(date);
    }

    // ---- Retry only the failed dates of a batch ------------------------------

    void retryBatch(long batchId) {
        IngestionBatchEntity batch = batches.findById(batchId)
                .orElseThrow(() -> new NotFoundException("Unknown batch: " + batchId));
        List<JobLaunch> failed = new ArrayList<>();
        for (IngestionJobEntity job : jobs.findByBatchIdOrderByTradeDateAsc(batchId)) {
            if (job.getStatus() == IngestionStatus.FAILED) {
                archive.findLatest(job.getTradeDate()).ifPresent(a -> {
                    job.setStatus(IngestionStatus.QUEUED);
                    jobs.save(job);
                    failed.add(new JobLaunch(job.getId(), a.path(), job.getTradeDate()));
                });
            }
        }
        if (failed.isEmpty()) {
            throw new DomainException("NOTHING_TO_RETRY", "batch has no failed, re-runnable dates");
        }
        LocalDate latest = failed.stream().map(JobLaunch::date).max(LocalDate::compareTo).orElseThrow();
        batch.setStatus(IngestionStatus.RUNNING);
        batch.setFinishedAt(null);
        batches.save(batch);
        executor.submit(() -> runBatch(batchId, failed, latest));
    }

    // ---- Async execution -----------------------------------------------------

    private void runBatch(long batchId, List<JobLaunch> launches, LocalDate latest) {
        boolean anyFailed = false;
        for (JobLaunch launch : launches) {
            // Suppress notifications for every backfill date except the latest, so a
            // backfill never spams users with months of stale alerts.
            boolean suppress = !launch.date().equals(latest);
            boolean ok = runJob(launch, suppress);
            anyFailed |= !ok;
        }
        batches.findById(batchId).ifPresent(batch -> {
            batch.setStatus(anyFailedStatus(batchId));
            batch.setFinishedAt(Instant.now(clock.clock()));
            batches.save(batch);
        });
    }

    private IngestionStatus anyFailedStatus(long batchId) {
        List<IngestionJobEntity> all = jobs.findByBatchIdOrderByTradeDateAsc(batchId);
        boolean anyFailed = all.stream().anyMatch(j -> j.getStatus() == IngestionStatus.FAILED);
        boolean anyOk = all.stream().anyMatch(j -> j.getStatus() == IngestionStatus.COMPLETED);
        if (anyFailed && anyOk) {
            return IngestionStatus.PARTIAL;
        }
        return anyFailed ? IngestionStatus.FAILED : IngestionStatus.COMPLETED;
    }

    /** @return true if the job completed successfully. */
    private boolean runJob(JobLaunch launch, boolean suppressNotifications) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("jobId", launch.jobId())
                    .addString("tradeDate", launch.date().toString())
                    .addString("archivedPath", launch.path().toString())
                    .addLong("launchTs", System.nanoTime())
                    .toJobParameters();
            jobLauncher.run(jobFactory.ingestionJob(), params);
        } catch (Exception e) {
            log.error("Ingestion job {} for {} failed to launch/run", launch.jobId(), launch.date(), e);
            markFailed(launch.jobId());
            return false;
        }
        Optional<IngestionJobEntity> job = jobs.findById(launch.jobId());
        if (job.isPresent() && job.get().getStatus() == IngestionStatus.COMPLETED) {
            events.publish(new TradesIngestedEvent(
                    launch.date(), launch.jobId(), job.get().getRowsAccepted(), suppressNotifications));
            return true;
        }
        return false;
    }

    private void markFailed(long jobId) {
        jobs.findById(jobId).ifPresent(job -> {
            job.setStatus(IngestionStatus.FAILED);
            job.setFinishedAt(Instant.now(clock.clock()));
            jobs.save(job);
        });
    }

    // ---- Helpers -------------------------------------------------------------

    private IngestionJobEntity createJob(Long batchId, LocalDate date, String filename, String hash) {
        IngestionJobEntity job = new IngestionJobEntity();
        job.setBatchId(batchId);
        job.setTradeDate(date);
        job.setSourceFilename(filename);
        job.setFileHash(hash);
        job.setStatus(IngestionStatus.QUEUED);
        return jobs.save(job);
    }

    private IngestionBatchEntity createBatch(int fileCount, LocalDate from, LocalDate to) {
        IngestionBatchEntity batch = new IngestionBatchEntity();
        batch.setFileCount(fileCount);
        batch.setDateFrom(from);
        batch.setDateTo(to);
        batch.setStatus(IngestionStatus.QUEUED);
        batch.setSubmittedAt(Instant.now(clock.clock()));
        return batches.save(batch);
    }

    private ArchivedFile archiveOrThrow(LocalDate date, MultipartFile file) {
        try {
            return archive.archive(date, file.getInputStream());
        } catch (IOException e) {
            throw new DomainException("ARCHIVE_FAILED", "could not archive " + file.getOriginalFilename());
        }
    }

    private void requireSizeWithinLimit(long size) {
        if (size > properties.maxFileBytes()) {
            throw new DomainException("FILE_TOO_LARGE",
                    "file exceeds " + properties.maxFileBytes() + " bytes");
        }
    }

    private void checkTradingDay(LocalDate date) {
        try {
            if (!calendar.isTradingDay(date)) {
                if (properties.rejectNonTradingDays()) {
                    throw new DomainException("NON_TRADING_DAY", date + " is not a trading day");
                }
                log.warn("Ingesting {} which is a non-trading day (allowed by config)", date);
            }
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException outOfCoverage) {
            log.warn("Calendar could not classify {}: {}", date, outOfCoverage.getMessage());
        }
    }
}
