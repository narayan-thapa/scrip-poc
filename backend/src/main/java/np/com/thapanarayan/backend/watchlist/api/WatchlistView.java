package np.com.thapanarayan.backend.watchlist.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A user's named watchlist and its symbols. */
public record WatchlistView(UUID id, String name, List<WatchlistItemView> items, Instant createdAt) {
}
