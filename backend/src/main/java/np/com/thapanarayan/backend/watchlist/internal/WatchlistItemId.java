package np.com.thapanarayan.backend.watchlist.internal;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite identity for {@link WatchlistItemEntity}: one row per (watchlist, symbol). */
class WatchlistItemId implements Serializable {

    private UUID watchlistId;
    private String symbol;

    WatchlistItemId() {
    }

    WatchlistItemId(UUID watchlistId, String symbol) {
        this.watchlistId = watchlistId;
        this.symbol = symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WatchlistItemId that)) {
            return false;
        }
        return Objects.equals(watchlistId, that.watchlistId) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(watchlistId, symbol);
    }
}
