package np.com.thapanarayan.backend.watchlist.internal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import np.com.thapanarayan.backend.watchlist.api.WatchlistReader;
import np.com.thapanarayan.backend.watchlist.internal.domain.Watchlist;
import np.com.thapanarayan.backend.watchlist.internal.domain.WatchlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User-scoped watchlist CRUD + the published {@link WatchlistReader}. Ownership is always enforced. */
@Service
public class WatchlistService implements WatchlistReader {

    private final WatchlistRepository repo;

    WatchlistService(WatchlistRepository repo) {
        this.repo = repo;
    }

    public List<Watchlist> forUser(UUID userId) {
        return repo.findByUserId(userId);
    }

    @Transactional
    public Watchlist create(UUID userId, String name) {
        return repo.save(new Watchlist(userId, name));
    }

    @Transactional
    public Watchlist rename(UUID userId, UUID id, String name) {
        Watchlist w = owned(userId, id);
        w.setName(name);
        return repo.save(w);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        repo.delete(owned(userId, id));
    }

    @Transactional
    public Watchlist addItem(UUID userId, UUID id, String symbol) {
        Watchlist w = owned(userId, id);
        w.addSymbol(symbol);
        return repo.save(w);
    }

    @Transactional
    public Watchlist removeItem(UUID userId, UUID id, String symbol) {
        Watchlist w = owned(userId, id);
        w.removeSymbol(symbol);
        return repo.save(w);
    }

    private Watchlist owned(UUID userId, UUID id) {
        Watchlist w = repo.findById(id).orElseThrow(() -> ApiException.notFound("Unknown watchlist: " + id));
        if (!w.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Not your watchlist");
        }
        return w;
    }

    @Override
    public Map<String, Set<UUID>> watchersBySymbol() {
        Map<String, Set<UUID>> map = new HashMap<>();
        for (Watchlist w : repo.findAll()) {
            for (String symbol : w.getSymbols()) {
                map.computeIfAbsent(symbol, k -> new HashSet<>()).add(w.getUserId());
            }
        }
        return map;
    }
}
