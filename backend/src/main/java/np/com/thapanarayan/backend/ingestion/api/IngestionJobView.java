package np.com.thapanarayan.backend.ingestion.api;

import java.time.Instant;
import java.time.LocalDate;

/** Read model for a per-file ingestion job. */
public record IngestionJobView(
        long id,
        Long batchId,
        LocalDate tradeDate,
        String sourceFilename,
        String fileHash,
        int rowsRead,
        int rowsAccepted,
        int rowsRejected,
        int rowsDuplicate,
        IngestionStatus status,
        Instant startedAt,
        Instant finishedAt) {
}
