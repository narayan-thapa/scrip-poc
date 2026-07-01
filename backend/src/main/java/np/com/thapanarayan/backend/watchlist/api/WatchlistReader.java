package np.com.thapanarayan.backend.watchlist.api;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Published read access so the notification module can find who watches a symbol. */
public interface WatchlistReader {

    /** Map of symbol → set of user ids watching it (across all their watchlists). */
    Map<String, Set<UUID>> watchersBySymbol();
}
