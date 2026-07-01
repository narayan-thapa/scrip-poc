package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;
import java.util.List;

/** Published read access to daily candles (consumed by the indicator engine + charting). */
public interface CandleSeriesReader {

    /** Candles for a symbol in the inclusive date range, ascending by date. */
    List<CandleBar> series(String symbol, LocalDate from, LocalDate to);

    /** Symbols that have a candle on a date (drives per-symbol indicator computation). */
    List<String> symbolsOn(LocalDate date);
}
