package np.com.thapanarayan.backend.signal.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Published read access to signals for cross-module annotation (screeners/dashboard/notifications/charts). */
public interface SignalReader {

    /** Signals for a date, keyed by symbol. */
    Map<String, SignalView> byDate(LocalDate date);

    /** A symbol's signals in a date range as chart markers (ascending). */
    List<SignalMarker> markersFor(String symbol, LocalDate from, LocalDate to);
}
