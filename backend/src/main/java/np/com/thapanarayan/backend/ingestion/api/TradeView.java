package np.com.thapanarayan.backend.ingestion.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only projection of one matched trade, published for downstream aggregation (marketdata)
 * without exposing the ingestion module's internals.
 */
public record TradeView(
        String symbol,
        int buyerBroker,
        int sellerBroker,
        long quantity,
        BigDecimal price,
        BigDecimal amount,
        LocalDateTime tradeTime) {
}
