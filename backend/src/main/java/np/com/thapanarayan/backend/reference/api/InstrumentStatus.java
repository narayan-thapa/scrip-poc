package np.com.thapanarayan.backend.reference.api;

/** Lifecycle state of a listed instrument. */
public enum InstrumentStatus {
    /** Normally trading. */
    ACTIVE,
    /** Temporarily halted. */
    SUSPENDED,
    /** No longer listed (retained for survivorship-correct backtests). */
    DELISTED,
    /** Auto-discovered from the floorsheet; awaiting admin enrichment. */
    PROVISIONAL
}
