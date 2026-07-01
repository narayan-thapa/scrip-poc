package np.com.thapanarayan.backend.ingestion.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One matched floorsheet trade. Persisted via a JDBC upsert (not JPA) for bulk performance and
 * {@code ON CONFLICT} idempotency on the composite key {@code (contract_id, trade_date)}.
 */
public record FloorsheetTrade(
        String contractId,
        String symbol,
        int buyerBroker,
        int sellerBroker,
        long quantity,
        BigDecimal price,
        BigDecimal amount,
        LocalDateTime tradeTime,
        LocalDate tradeDate,
        UUID sourceFileId) {
}
