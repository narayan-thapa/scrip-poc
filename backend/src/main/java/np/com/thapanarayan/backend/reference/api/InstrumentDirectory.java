package np.com.thapanarayan.backend.reference.api;

/**
 * Published lookup + auto-discovery hook for instruments. Ingestion (Phase 2) calls
 * {@link #ensureProvisional} when the floorsheet names a symbol the reference data doesn't know yet,
 * creating a provisional record flagged for later enrichment instead of rejecting the trade.
 */
public interface InstrumentDirectory {

    boolean exists(String symbol);

    /**
     * Ensure an instrument row exists for {@code symbol}; if absent, create a provisional one.
     * Idempotent. Returns true when a new provisional record was created.
     */
    boolean ensureProvisional(String symbol);

    /** Listed shares for a symbol, when known — used for the cap-weighted NEPSE index proxy. */
    java.util.Optional<Long> listedShares(String symbol);
}
