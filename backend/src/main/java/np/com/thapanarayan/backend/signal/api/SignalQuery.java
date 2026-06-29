package np.com.thapanarayan.backend.signal.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Published read surface over generated signals. Downstream modules (charting,
 * notifications, watchlists) depend on this interface, never on the signal
 * {@code internal} package.
 */
public interface SignalQuery {

    /** A single signal by its surrogate id. */
    Optional<SignalView> findById(UUID id);

    /** The latest signal for a symbol, if any has been generated. */
    Optional<SignalView> latestForSymbol(String symbol);

    /** All signals for a symbol across an inclusive date range, descending by date. */
    List<SignalView> forSymbol(String symbol, LocalDate fromInclusive, LocalDate toInclusive);

    /** All signals generated for a specific trade date, ascending by symbol. */
    List<SignalView> forDate(LocalDate tradeDate);

    /** The latest generated trade date's signals, optionally filtered by action. */
    List<SignalView> latest(Optional<SignalAction> action);
}
