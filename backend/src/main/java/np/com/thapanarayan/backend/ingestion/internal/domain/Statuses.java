package np.com.thapanarayan.backend.ingestion.internal.domain;

/** Status enums for ingestion bookkeeping. */
public final class Statuses {

    private Statuses() {
    }

    public enum BatchStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED,
        /** Some dates completed, others failed. */
        PARTIAL
    }

    public enum JobStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
