package np.com.thapanarayan.backend.ingestion.internal.domain;

/**
 * Stable reason codes for quarantined rows. A bad row is recorded with one of these and skipped —
 * it never fails the whole file (Decision A: treat the scraped CSV as hostile).
 */
public enum RejectionReason {
    MALFORMED_COLUMNS,      // wrong number of fields after normalization
    BLANK_SYMBOL,
    BAD_NUMBER,             // quantity/price/amount not parseable
    NON_POSITIVE_QUANTITY,
    NON_POSITIVE_PRICE,
    BAD_BROKER_ID,
    BAD_TIMESTAMP,          // no configured pattern matched
    DATE_MISMATCH,          // row timestamp's date != filename date
    AMOUNT_MISMATCH,        // |amount - qty*price| exceeds tolerance
    BLANK_CONTRACT_ID
}
