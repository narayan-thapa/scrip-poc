package np.com.thapanarayan.backend.reference.api;

import java.util.Optional;

/**
 * Published lookup surface for instrument master data. Other modules depend on
 * this interface, never on the reference internals.
 */
public interface InstrumentCatalog {

    Optional<InstrumentView> findBySymbol(String symbol);

    boolean exists(String symbol);

    /**
     * Returns the instrument for {@code symbol}, creating a {@link InstrumentStatus#PROVISIONAL}
     * record if it is unknown. Used by ingestion when the floorsheet names a symbol not yet in
     * the registry. Safe under concurrent first-sight of the same symbol.
     */
    InstrumentView getOrCreateProvisional(String symbol);
}
