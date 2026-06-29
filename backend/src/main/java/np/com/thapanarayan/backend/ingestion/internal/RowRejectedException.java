package np.com.thapanarayan.backend.ingestion.internal;

/**
 * Thrown when a floorsheet row cannot be accepted. Carries a stable reason code
 * and detail; the batch skip listener routes it to {@code ingestion_rejection}
 * rather than failing the whole file.
 */
class RowRejectedException extends RuntimeException {

    private final RejectionReason reason;

    RowRejectedException(RejectionReason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    RejectionReason reason() {
        return reason;
    }
}
