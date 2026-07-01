package np.com.thapanarayan.backend.screener.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Caches pre-warmed screener/dashboard results per key (date + params). In-memory by default so the
 * page loads instantly after the EOD pre-warm; a Redis-backed impl is a drop-in replacement (the
 * architecture's Redis cache). Cleared per-date when new data is pre-warmed.
 */
@Component
public class ScreenerCache {

    private final Map<String, Object> store = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T getOrCompute(String key, Supplier<T> compute) {
        return (T) store.computeIfAbsent(key, k -> compute.get());
    }

    public void put(String key, Object value) {
        store.put(key, value);
    }

    /** Drop cached entries for a date (called before re-warming). */
    public void evictDate(String datePrefix) {
        store.keySet().removeIf(k -> k.startsWith(datePrefix));
    }
}
