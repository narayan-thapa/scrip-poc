package np.com.thapanarayan.backend.ingestion.api;

/** Read model for a single quarantined row. */
public record RejectionView(
        long id,
        long jobId,
        String rawLine,
        String reasonCode,
        String detail) {
}
