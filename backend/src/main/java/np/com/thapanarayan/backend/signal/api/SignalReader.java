package np.com.thapanarayan.backend.signal.api;

import java.time.LocalDate;
import java.util.Map;

/** Published read access to signals for cross-module annotation (screeners/dashboard/notifications). */
public interface SignalReader {

    /** Signals for a date, keyed by symbol. */
    Map<String, SignalView> byDate(LocalDate date);
}
