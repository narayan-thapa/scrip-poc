package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.ingestion.api.IngestionJobView;

/**
 * Pipeline orchestration & status (§10.11). Status is read-only; the manual run lets
 * an operator launch (or re-launch) a date's pipeline outside the schedule. Both are
 * ADMIN-gated by the security configuration.
 */
@RestController
@RequestMapping("/api/v1/system")
class SystemController {

    private final PipelineStatusService status;
    private final IngestionService ingestion;
    private final np.com.thapanarayan.backend.platform.api.NepseClock clock;

    SystemController(PipelineStatusService status, IngestionService ingestion,
            np.com.thapanarayan.backend.platform.api.NepseClock clock) {
        this.status = status;
        this.ingestion = ingestion;
        this.clock = clock;
    }

    @GetMapping("/pipeline/status")
    PipelineStatusView pipelineStatus() {
        return status.status();
    }

    /** Manually launch the pipeline for {@code date} (defaults to today, NPT) from the archive. */
    @PostMapping("/pipeline/run")
    ResponseEntity<IngestionJobView> run(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date != null ? date : clock.today();
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ingestion.reprocessDate(target));
    }
}
