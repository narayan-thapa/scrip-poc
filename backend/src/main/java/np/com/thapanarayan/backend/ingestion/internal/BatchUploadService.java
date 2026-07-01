package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionBatch;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionBatchRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Intake for single and multi-file uploads (Decision D, option 1). Enforces guardrails, validates
 * filenames, rejects duplicate dates within a batch, archives each file and creates QUEUED jobs in
 * ascending date order, then kicks off async processing — returning immediately with a batch id.
 */
@Service
public class BatchUploadService {

    /** One uploaded file: validated filename + raw bytes (controller adapts MultipartFile to this). */
    public record UploadedFile(String filename, byte[] content) {}

    public record IntakeResult(UUID batchId, List<FileIntake> files) {}

    public record FileIntake(String filename, LocalDate tradeDate, int bytes) {}

    private final IngestionService ingestionService;
    private final IngestionPipeline pipeline;
    private final IngestionBatchRepository batches;
    private final IngestionProperties props;

    BatchUploadService(IngestionService ingestionService, IngestionPipeline pipeline,
                       IngestionBatchRepository batches, IngestionProperties props) {
        this.ingestionService = ingestionService;
        this.pipeline = pipeline;
        this.batches = batches;
        this.props = props;
    }

    @Transactional
    public IntakeResult submit(List<UploadedFile> files, String submittedBy) {
        validateCaps(files);

        // Validate filenames, derive dates, and reject duplicate dates within the batch.
        Set<LocalDate> seen = new HashSet<>();
        List<Dated> dated = files.stream()
                .map(f -> new Dated(FilenameValidator.tradeDateOf(f.filename()), f))
                .sorted(Comparator.comparing(Dated::date)) // oldest-first processing order
                .toList();
        for (Dated d : dated) {
            if (!seen.add(d.date())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "Duplicate date within batch: " + d.date());
            }
        }

        LocalDate from = dated.get(0).date();
        LocalDate to = dated.get(dated.size() - 1).date();
        IngestionBatch batch = batches.save(new IngestionBatch(dated.size(), from, to, submittedBy));

        List<FileIntake> intake = dated.stream().map(d -> {
            ingestionService.createJob(batch.getId(), d.date(), d.file().filename(), d.file().content());
            return new FileIntake(d.file().filename(), d.date(), d.file().content().length);
        }).toList();

        pipeline.runBatchAsync(batch.getId());
        return new IntakeResult(batch.getId(), intake);
    }

    private void validateCaps(List<UploadedFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "No files provided");
        }
        if (files.size() > props.maxFilesPerBatch()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Too many files: " + files.size() + " > " + props.maxFilesPerBatch());
        }
        long total = 0;
        for (UploadedFile f : files) {
            long size = f.content() == null ? 0 : f.content().length;
            if (size == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "Empty file: " + f.filename());
            }
            if (size > props.maxFileBytes()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "File exceeds size cap: " + f.filename());
            }
            total += size;
        }
        if (total > props.maxBatchBytes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Batch exceeds total size cap");
        }
    }

    private record Dated(LocalDate date, UploadedFile file) {}
}
