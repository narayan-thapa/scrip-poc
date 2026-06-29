package np.com.thapanarayan.backend.reference.api;

import java.time.Instant;

/** Read model for a listed instrument, published to other modules and the API. */
public record InstrumentView(
        String symbol,
        String name,
        String sector,
        Long listedShares,
        InstrumentStatus status,
        String priceBand,
        Instant createdAt) {
}
