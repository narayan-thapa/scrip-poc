package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionBatch;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionBatchRepository;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionJobRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-runs ingestion for a date (or range) from the archived raw file — no re-upload needed. Used by
 * the {@code /reprocess} and {@code /trigger} endpoints and the signed scraper webhook. Idempotent.
 */
@Service
public class ReprocessService {

    private final IngestionJobRepository jobs;
    private final IngestionBatchRepository batches;
    private final IngestionService ingestionService;
    private final IngestionPipeline pipeline;

    ReprocessService(IngestionJobRepository jobs, IngestionBatchRepository batches,
                     IngestionService ingestionService, IngestionPipeline pipeline) {
        this.jobs = jobs;
        this.batches = batches;
        this.ingestionService = ingestionService;
        this.pipeline = pipeline;
    }

    @Transactional
    public UUID reprocess(LocalDate from, LocalDate to) {
        List<IngestionJob> sources = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            jobs.findFirstByTradeDateOrderByStartedAtDesc(d).ifPresent(sources::add);
        }
        if (sources.isEmpty()) {
            throw ApiException.notFound("No archived files to reprocess in " + from + ".." + to);
        }
        IngestionBatch batch = batches.save(new IngestionBatch(sources.size(), from, to, "reprocess"));
        for (IngestionJob src : sources) {
            ingestionService.createJobFromArchive(
                    batch.getId(), src.getTradeDate(), src.getSourceFilename(), src.getFileHash(), src.getArchiveKey());
        }
        pipeline.runBatchAsync(batch.getId());
        return batch.getId();
    }
}
