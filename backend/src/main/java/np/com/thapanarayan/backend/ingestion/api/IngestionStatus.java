package np.com.thapanarayan.backend.ingestion.api;

/** Lifecycle status shared by ingestion batches and per-file jobs. */
public enum IngestionStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    /** Batch only: completed but at least one file failed. */
    PARTIAL,
    FAILED,
    /** Skipped because the date is a known non-trading day (configurable). */
    SKIPPED
}
