package np.com.thapanarayan.backend.ingestion.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A persisted floorsheet trade, published for downstream aggregation. This is the
 * read projection of {@code floorsheet_trade}; the {@code contract_id} dedup key
 * and archival metadata are deliberately omitted — consumers (market-data
 * aggregation) only need the economic fields.
 */
public record FloorsheetTradeRecord(
        String symbol,
        int buyerBroker,
        int sellerBroker,
        long quantity,
        BigDecimal price,
        BigDecimal amount,
        LocalDateTime tradeTime,
        LocalDate tradeDate) {
}
