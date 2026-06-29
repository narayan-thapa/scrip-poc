package np.com.thapanarayan.backend.watchlist.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** A symbol on a watchlist. */
@Entity
@Table(name = "watchlist_item")
@IdClass(WatchlistItemId.class)
class WatchlistItemEntity {

    @Id
    @Column(name = "watchlist_id", nullable = false)
    private UUID watchlistId;

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected WatchlistItemEntity() {
    }

    UUID getWatchlistId() {
        return watchlistId;
    }

    void setWatchlistId(UUID watchlistId) {
        this.watchlistId = watchlistId;
    }

    String getSymbol() {
        return symbol;
    }

    void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    Instant getAddedAt() {
        return addedAt;
    }

    void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }
}
