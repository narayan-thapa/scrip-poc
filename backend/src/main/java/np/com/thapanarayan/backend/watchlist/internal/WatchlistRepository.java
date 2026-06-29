package np.com.thapanarayan.backend.watchlist.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WatchlistRepository extends JpaRepository<WatchlistEntity, UUID> {

    List<WatchlistEntity> findByUserIdOrderByName(UUID userId);

    Optional<WatchlistEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndName(UUID userId, String name);
}
