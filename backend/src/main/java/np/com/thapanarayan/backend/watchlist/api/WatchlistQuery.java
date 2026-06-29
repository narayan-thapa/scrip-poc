package np.com.thapanarayan.backend.watchlist.api;

import java.util.Set;
import java.util.UUID;

/**
 * Published read port over watchlists. The notification module (Stage 9) uses this to
 * resolve a user's watched symbols when evaluating WATCHLIST_SIGNAL alert rules.
 */
public interface WatchlistQuery {

    /** Distinct symbols across all of a user's watchlists. */
    Set<String> symbolsForUser(UUID userId);
}
