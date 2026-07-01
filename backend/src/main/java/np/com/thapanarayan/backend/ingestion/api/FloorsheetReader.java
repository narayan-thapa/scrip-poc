package np.com.thapanarayan.backend.ingestion.api;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Published read access to ingested floorsheet trades (consumed by marketdata aggregation + APIs). */
public interface FloorsheetReader {

    /** Distinct symbols that traded on a date (drives per-symbol aggregation). */
    List<String> symbolsTradedOn(LocalDate date);

    /** All trades for one symbol on a date, ordered by trade time (for OHLC/VWAP/profile/flow). */
    List<TradeView> tradesForSymbolAndDate(String symbol, LocalDate date);

    /** Paged trades for the {@code /market/trades} endpoint. */
    Page<TradeView> page(String symbol, LocalDate date, Pageable pageable);
}
