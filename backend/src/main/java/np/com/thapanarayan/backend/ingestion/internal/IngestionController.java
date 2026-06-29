package np.com.thapanarayan.backend.ingestion.internal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import np.com.thapanarayan.backend.ingestion.api.IngestionBatchView;
import np.com.thapanarayan.backend.ingestion.api.IngestionJobView;
import np.com.thapanarayan.backend.ingestion.api.RejectionView;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.platform.api.PageResponse;

/**
 * Admin ingestion API. TODO(Stage 8 / IAM): gate all endpoints with role ADMIN
 * once Spring Security is wired; today they are unauthenticated.
 */
@RestController
@RequestMapping("/api/v1/ingestion")
@Validated
class IngestionController {

    private static final Pattern WEBHOOK_DATE = Pattern.compile("\"date\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"");

    private final IngestionService service;
    private final IngestionJobRepository jobs;
    private final IngestionBatchRepository batches;
    private final IngestionRejectionRepository rejections;
    private final IngestionProperties properties;

    IngestionController(IngestionService service, IngestionJobRepository jobs,
            IngestionBatchRepository batches, IngestionRejectionRepository rejections,
            IngestionProperties properties) {
        this.service = service;
        this.jobs = jobs;
        this.batches = batches;
        this.rejections = rejections;
        this.properties = properties;
    }

    @PostMapping("/uploads")
    ResponseEntity<IngestionJobView> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submitSingle(file));
    }

    @PostMapping("/uploads/batch")
    ResponseEntity<?> uploadBatch(@RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.submitBatch(files));
    }

    @PostMapping("/trigger")
    ResponseEntity<?> trigger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(triggerOrReprocess(date, from, to));
    }

    @PostMapping("/reprocess")
    ResponseEntity<?> reprocess(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(triggerOrReprocess(date, from, to));
    }

    private Object triggerOrReprocess(LocalDate date, LocalDate from, LocalDate to) {
        if (date != null) {
            return service.reprocessDate(date);
        }
        if (from != null && to != null) {
            return service.reprocessRange(from, to);
        }
        throw new DomainException("BAD_REQUEST", "provide 'date' or both 'from' and 'to'");
    }

    @PostMapping("/webhook")
    ResponseEntity<IngestionJobView> webhook(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestBody(required = false) byte[] body) {
        byte[] payload = body != null ? body : new byte[0];
        WebhookVerifier.verify(properties.webhookSecret(), properties.webhookReplaySeconds(),
                signature, timestamp, payload);
        Matcher m = WEBHOOK_DATE.matcher(new String(payload, StandardCharsets.UTF_8));
        if (!m.find()) {
            throw new DomainException("WEBHOOK_NO_DATE", "body must contain a \"date\":\"YYYY-MM-DD\"");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.triggerFromWebhook(LocalDate.parse(m.group(1))));
    }

    @GetMapping("/batches")
    PageResponse<IngestionBatchView> listBatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id").descending());
        return PageResponse.from(batches.findAll(pageable).map(IngestionMapper::toView));
    }

    @GetMapping("/batches/{batchId}")
    IngestionBatchView getBatch(@PathVariable long batchId) {
        return batches.findById(batchId).map(IngestionMapper::toView)
                .orElseThrow(() -> new NotFoundException("Unknown batch: " + batchId));
    }

    @GetMapping("/batches/{batchId}/files")
    List<IngestionJobView> batchFiles(@PathVariable long batchId) {
        if (!batches.existsById(batchId)) {
            throw new NotFoundException("Unknown batch: " + batchId);
        }
        return jobs.findByBatchIdOrderByTradeDateAsc(batchId).stream().map(IngestionMapper::toView).toList();
    }

    @PostMapping("/batches/{batchId}/retry")
    ResponseEntity<Void> retryBatch(@PathVariable long batchId) {
        service.retryBatch(batchId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/jobs")
    PageResponse<IngestionJobView> listJobs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size);
        return PageResponse.from(jobs.findAllByOrderByIdDesc(pageable).map(IngestionMapper::toView));
    }

    @GetMapping("/jobs/{id}")
    IngestionJobView getJob(@PathVariable long id) {
        return jobs.findById(id).map(IngestionMapper::toView)
                .orElseThrow(() -> new NotFoundException("Unknown job: " + id));
    }

    @GetMapping("/jobs/{id}/rejections")
    PageResponse<RejectionView> jobRejections(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        if (!jobs.existsById(id)) {
            throw new NotFoundException("Unknown job: " + id);
        }
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return PageResponse.from(rejections.findByJobId(id, pageable).map(IngestionMapper::toView));
    }
}
