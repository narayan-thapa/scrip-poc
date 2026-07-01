package np.com.thapanarayan.backend.ingestion.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.ingestion.internal.BatchUploadService;
import np.com.thapanarayan.backend.ingestion.internal.BatchUploadService.UploadedFile;
import np.com.thapanarayan.backend.ingestion.internal.IngestionPipeline;
import np.com.thapanarayan.backend.ingestion.internal.ReprocessService;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.ingestion.internal.parse.CsvSanitizer;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionBatchRepository;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionJobRepository;
import np.com.thapanarayan.backend.ingestion.internal.repo.IngestionRejectionRepository;
import np.com.thapanarayan.backend.ingestion.internal.web.IngestionDtos.BatchDetail;
import np.com.thapanarayan.backend.ingestion.internal.web.IngestionDtos.BatchSummary;
import np.com.thapanarayan.backend.ingestion.internal.web.IngestionDtos.DateRangeRequest;
import np.com.thapanarayan.backend.ingestion.internal.web.IngestionDtos.JobSummary;
import np.com.thapanarayan.backend.ingestion.internal.web.IngestionDtos.RejectionDto;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.page.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin ingestion API: uploads, backfill, reprocess, and batch/job/rejection inspection. */
@RestController
@RequestMapping("/api/v1/ingestion")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Ingestion", description = "Admin: upload, backfill, reprocess and inspect ingestion runs")
class IngestionController {

    private final BatchUploadService uploads;
    private final ReprocessService reprocess;
    private final IngestionPipeline pipeline;
    private final IngestionBatchRepository batches;
    private final IngestionJobRepository jobs;
    private final IngestionRejectionRepository rejections;

    IngestionController(BatchUploadService uploads, ReprocessService reprocess, IngestionPipeline pipeline,
                        IngestionBatchRepository batches, IngestionJobRepository jobs,
                        IngestionRejectionRepository rejections) {
        this.uploads = uploads;
        this.reprocess = reprocess;
        this.pipeline = pipeline;
        this.batches = batches;
        this.jobs = jobs;
        this.rejections = rejections;
    }

    @PostMapping("/uploads")
    ResponseEntity<BatchUploadService.IntakeResult> upload(@RequestParam("file") MultipartFile file,
                                                           @AuthenticationPrincipal Jwt jwt) {
        var result = uploads.submit(List.of(toUploadedFile(file)), subject(jwt));
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/uploads/batch")
    ResponseEntity<BatchUploadService.IntakeResult> uploadBatch(@RequestParam("files") MultipartFile[] files,
                                                                @AuthenticationPrincipal Jwt jwt) {
        var result = uploads.submit(List.of(files).stream().map(IngestionController::toUploadedFile).toList(), subject(jwt));
        return ResponseEntity.accepted().body(result);
    }

    @PostMapping("/trigger")
    ResponseEntity<UUID> trigger(@RequestBody DateRangeRequest req) {
        return ResponseEntity.accepted().body(reprocess.reprocess(req.effectiveFrom(), req.effectiveTo()));
    }

    @PostMapping("/reprocess")
    ResponseEntity<UUID> reprocess(@RequestBody DateRangeRequest req) {
        return ResponseEntity.accepted().body(reprocess.reprocess(req.effectiveFrom(), req.effectiveTo()));
    }

    @GetMapping("/batches")
    PageResponse<BatchSummary> listBatches(@PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(batches.findAllByOrderBySubmittedAtDesc(pageable), BatchSummary::from);
    }

    @GetMapping("/batches/{id}")
    BatchDetail batch(@PathVariable UUID id) {
        var batch = batches.findById(id).orElseThrow(() -> ApiException.notFound("Unknown batch: " + id));
        var jobList = jobs.findByBatchIdOrderByTradeDateAsc(id).stream().map(JobSummary::from).toList();
        return new BatchDetail(BatchSummary.from(batch), jobList);
    }

    @GetMapping("/batches/{id}/files")
    List<JobSummary> batchFiles(@PathVariable UUID id) {
        return jobs.findByBatchIdOrderByTradeDateAsc(id).stream().map(JobSummary::from).toList();
    }

    @PostMapping("/batches/{id}/retry")
    ResponseEntity<Void> retry(@PathVariable UUID id) {
        if (!batches.existsById(id)) {
            throw ApiException.notFound("Unknown batch: " + id);
        }
        pipeline.runBatchAsync(id); // re-runs QUEUED/FAILED dates only
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/jobs")
    PageResponse<JobSummary> listJobs(@PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.from(jobs.findAllByOrderByTradeDateDescStartedAtDesc(pageable), JobSummary::from);
    }

    @GetMapping("/jobs/{id}")
    JobSummary job(@PathVariable UUID id) {
        return jobs.findById(id).map(JobSummary::from).orElseThrow(() -> ApiException.notFound("Unknown job: " + id));
    }

    @GetMapping("/jobs/{id}/rejections")
    PageResponse<RejectionDto> rejections(@PathVariable UUID id, @PageableDefault(size = 100) Pageable pageable) {
        return PageResponse.from(rejections.findByJobId(id, pageable), RejectionDto::from);
    }

    /** Downloadable rejection report. Cells are formula-injection-neutralized (Decision A). */
    @GetMapping(value = "/jobs/{id}/rejections.csv", produces = "text/csv")
    ResponseEntity<String> rejectionsCsv(@PathVariable UUID id) {
        IngestionJob job = jobs.findById(id).orElseThrow(() -> ApiException.notFound("Unknown job: " + id));
        StringBuilder sb = new StringBuilder("reason_code,detail,raw_line\n");
        for (var r : rejections.findByJobId(id)) {
            sb.append(CsvSanitizer.sanitizeCell(r.getReasonCode().name())).append(',')
                    .append(CsvSanitizer.sanitizeCell(r.getDetail())).append(',')
                    .append(CsvSanitizer.sanitizeCell(r.getRawLine())).append('\n');
        }
        String filename = "rejections-" + job.getTradeDate() + ".csv";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(sb.toString());
    }

    private static UploadedFile toUploadedFile(MultipartFile file) {
        try {
            return new UploadedFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    np.com.thapanarayan.backend.platform.api.error.ErrorCode.VALIDATION_FAILED,
                    "Could not read uploaded file");
        }
    }

    private static String subject(Jwt jwt) {
        return jwt != null ? jwt.getSubject() : null;
    }
}
