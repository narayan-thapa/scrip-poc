package np.com.thapanarayan.backend.ingestion.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Synchronous response to a batch upload: the batch id plus the intake outcome
 * for each file. Ingestion itself proceeds asynchronously (dates ascending).
 */
public record BatchSubmissionResult(
        long batchId,
        List<FileIntake> files) {

    /** Per-file intake result (accepted into the batch, or rejected at intake). */
    public record FileIntake(
            String filename,
            LocalDate tradeDate,
            boolean accepted,
            String message) {
    }
}
