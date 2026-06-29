package np.com.thapanarayan.backend.watchlist.api;

import java.time.Instant;

/** A symbol on a watchlist, with when it was added. */
public record WatchlistItemView(String symbol, Instant addedAt) {
}
