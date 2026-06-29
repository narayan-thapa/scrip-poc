package np.com.thapanarayan.backend.ingestion.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Published read port over persisted floorsheet trades. The {@code floorsheet_trade}
 * table is owned by ingestion; other modules (market-data aggregation, charting)
 * read it only through this interface, never by touching the table or the
 * ingestion {@code internal} package directly.
 *
 * <p>Queries are bounded by date so a single call never scans the whole history:
 * a day's floorsheet is large but finite, and aggregation runs one date at a time.</p>
 */
public interface FloorsheetTradeQuery {

    /** All trades for {@code tradeDate}, ordered by trade time then symbol. */
    List<FloorsheetTradeRecord> tradesForDate(LocalDate tradeDate);

    /**
     * All trades for one symbol across an inclusive date range, ordered by trade
     * time. Used for composite (multi-day) volume profiles.
     */
    List<FloorsheetTradeRecord> tradesForSymbolBetween(String symbol, LocalDate fromInclusive, LocalDate toInclusive);
}
