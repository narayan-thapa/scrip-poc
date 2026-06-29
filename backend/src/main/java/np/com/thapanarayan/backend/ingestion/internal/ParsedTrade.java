package np.com.thapanarayan.backend.ingestion.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A validated floorsheet trade, ready to upsert. */
record ParsedTrade(
        String contractId,
        String symbol,
        int buyerBroker,
        int sellerBroker,
        long quantity,
        BigDecimal price,
        BigDecimal amount,
        LocalDateTime tradeTime,
        LocalDate tradeDate) {
}
