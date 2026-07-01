package np.com.thapanarayan.backend.indicator.internal;

import java.util.function.Supplier;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;

/**
 * Cache for parametrized indicator series, keyed by {@code (symbol, indicator, paramsHash, lastDate)}.
 * The default is a no-op; a Redis-backed implementation can be dropped in (the architecture's
 * cross-request cache) without touching callers. Recompute on a new {@code lastDate} is automatic
 * because the date is part of the key.
 */
public interface SeriesCache {

    IndicatorResult getOrCompute(String key, Supplier<IndicatorResult> compute);

    static String key(String symbol, String id, String paramsHash, String lastDate) {
        return symbol + ':' + id + ':' + paramsHash + ':' + lastDate;
    }
}
