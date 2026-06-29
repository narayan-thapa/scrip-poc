package np.com.thapanarayan.backend.watchlist.internal;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.reference.api.InstrumentCatalog;
import np.com.thapanarayan.backend.watchlist.api.WatchlistItemView;
import np.com.thapanarayan.backend.watchlist.api.WatchlistQuery;
import np.com.thapanarayan.backend.watchlist.api.WatchlistView;

/**
 * Watchlist CRUD (§10.10). Every operation is scoped to the owning user — lookups go
 * through {@code (id, userId)} so one user can never read or mutate another's
 * watchlist. Added symbols are validated against the instrument registry.
 */
@Service
class WatchlistService implements WatchlistQuery {

    private final WatchlistRepository watchlists;
    private final WatchlistItemRepository items;
    private final InstrumentCatalog instruments;
    private final NepseClock clock;

    WatchlistService(WatchlistRepository watchlists, WatchlistItemRepository items,
            InstrumentCatalog instruments, NepseClock clock) {
        this.watchlists = watchlists;
        this.items = items;
        this.instruments = instruments;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WatchlistView> list(UUID userId) {
        return watchlists.findByUserIdOrderByName(userId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public WatchlistView get(UUID userId, UUID id) {
        return toView(owned(userId, id));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Set<String> symbolsForUser(UUID userId) {
        java.util.Set<String> symbols = new java.util.TreeSet<>();
        for (WatchlistEntity w : watchlists.findByUserIdOrderByName(userId)) {
            items.findByWatchlistIdOrderBySymbol(w.getId())
                    .forEach(i -> symbols.add(i.getSymbol()));
        }
        return symbols;
    }

    @Transactional
    public WatchlistView create(UUID userId, String name) {
        String trimmed = requireName(name);
        if (watchlists.existsByUserIdAndName(userId, trimmed)) {
            throw new DomainException("WATCHLIST_EXISTS", "A watchlist with that name already exists");
        }
        WatchlistEntity e = new WatchlistEntity();
        e.setId(UUID.randomUUID());
        e.setUserId(userId);
        e.setName(trimmed);
        e.setCreatedAt(Instant.now(clock.clock()));
        return toView(watchlists.save(e));
    }

    @Transactional
    public WatchlistView rename(UUID userId, UUID id, String name) {
        WatchlistEntity e = owned(userId, id);
        String trimmed = requireName(name);
        if (!trimmed.equals(e.getName()) && watchlists.existsByUserIdAndName(userId, trimmed)) {
            throw new DomainException("WATCHLIST_EXISTS", "A watchlist with that name already exists");
        }
        e.setName(trimmed);
        return toView(e);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        watchlists.delete(owned(userId, id)); // items cascade via FK
    }

    @Transactional
    public WatchlistView addItem(UUID userId, UUID id, String symbol) {
        WatchlistEntity e = owned(userId, id);
        String sym = normalizeSymbol(symbol);
        if (!instruments.exists(sym)) {
            throw new DomainException("UNKNOWN_SYMBOL", "Unknown instrument: " + sym);
        }
        if (!items.existsById(new WatchlistItemId(e.getId(), sym))) {
            WatchlistItemEntity item = new WatchlistItemEntity();
            item.setWatchlistId(e.getId());
            item.setSymbol(sym);
            item.setAddedAt(Instant.now(clock.clock()));
            items.save(item);
        }
        return toView(e);
    }

    @Transactional
    public WatchlistView removeItem(UUID userId, UUID id, String symbol) {
        WatchlistEntity e = owned(userId, id);
        items.deleteById(new WatchlistItemId(e.getId(), normalizeSymbol(symbol)));
        return toView(e);
    }

    private WatchlistEntity owned(UUID userId, UUID id) {
        return watchlists.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Watchlist not found"));
    }

    private WatchlistView toView(WatchlistEntity e) {
        List<WatchlistItemView> itemViews = items.findByWatchlistIdOrderBySymbol(e.getId()).stream()
                .map(i -> new WatchlistItemView(i.getSymbol(), i.getAddedAt()))
                .toList();
        return new WatchlistView(e.getId(), e.getName(), itemViews, e.getCreatedAt());
    }

    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new DomainException("INVALID_NAME", "Watchlist name is required");
        }
        return trimmed;
    }

    private static String normalizeSymbol(String symbol) {
        String sym = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (sym.isEmpty()) {
            throw new DomainException("INVALID_SYMBOL", "Symbol is required");
        }
        return sym;
    }
}
