package np.com.thapanarayan.backend.watchlist.internal;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WatchlistItemRepository extends JpaRepository<WatchlistItemEntity, WatchlistItemId> {

    List<WatchlistItemEntity> findByWatchlistIdOrderBySymbol(UUID watchlistId);
}
