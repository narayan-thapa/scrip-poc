package np.com.thapanarayan.backend.ingestion.internal;

/** Stable reason codes for quarantined rows, surfaced in the rejection report. */
enum RejectionReason {
    MALFORMED_STRUCTURE,
    BAD_TIMESTAMP,
    INVALID_NUMBER,
    INVALID_BROKER,
    NON_POSITIVE_QUANTITY,
    NON_POSITIVE_PRICE,
    AMOUNT_MISMATCH,
    DATE_MISMATCH,
    MISSING_CONTRACT_ID
}
