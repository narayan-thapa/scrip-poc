package np.com.thapanarayan.backend.ingestion.api;

import java.time.Instant;
import java.time.LocalDate;

/** Read model for an ingestion batch (a multi-file backfill submission). */
public record IngestionBatchView(
        long id,
        int fileCount,
        LocalDate dateFrom,
        LocalDate dateTo,
        IngestionStatus status,
        String submittedBy,
        Instant submittedAt,
        Instant finishedAt) {
}
