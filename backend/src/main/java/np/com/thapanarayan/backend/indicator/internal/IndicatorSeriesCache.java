package np.com.thapanarayan.backend.indicator.internal;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesView;

/**
 * Redis cache for ad-hoc parametrized indicator series (§2.4), keyed by
 * {@code (symbol, indicator, paramsHash, lastDate)}. Because the data is EOD and
 * deterministic, a hit is always valid until the date or params change.
 *
 * <p>Resilience over availability: every Redis call is wrapped so a connection
 * failure (e.g. Redis not running) is logged and treated as a cache miss / no-op
 * rather than failing the request — indicators are always recomputable from
 * candles. Reuses Spring's configured Jackson mapper (Jackson 3), which already
 * serializes {@code java.time} as ISO strings for the REST layer.</p>
 */
@Component
class IndicatorSeriesCache {

    private static final Logger log = LoggerFactory.getLogger(IndicatorSeriesCache.class);

    private final StringRedisTemplate redis;
    private final IndicatorProperties properties;
    private final ObjectMapper mapper;

    IndicatorSeriesCache(StringRedisTemplate redis, IndicatorProperties properties, ObjectMapper mapper) {
        this.redis = redis;
        this.properties = properties;
        this.mapper = mapper;
    }

    static String key(String symbol, String indicator, List<Integer> params, java.time.LocalDate lastDate) {
        return "ind:%s:%s:%s:%s".formatted(symbol, indicator, params, lastDate);
    }

    Optional<IndicatorSeriesView> get(String key) {
        if (!properties.cacheEnabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(json, IndicatorSeriesView.class));
        } catch (Exception e) {
            log.debug("Indicator cache get miss/error for {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    void put(String key, IndicatorSeriesView view) {
        if (!properties.cacheEnabled()) {
            return;
        }
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(view),
                    Duration.ofSeconds(properties.cacheTtlSeconds()));
        } catch (Exception e) {
            log.debug("Indicator cache put skipped for {}: {}", key, e.getMessage());
        }
    }

    /**
     * Explicitly drops every cached series for a symbol (§10.12). Called when that
     * symbol's snapshot is recomputed, so a same-date recompute (e.g. an ingestion
     * correction) never serves stale overlays. Cross-date staleness is already
     * impossible — the {@code lastDate} is part of the key.
     */
    void evictSymbol(String symbol) {
        if (!properties.cacheEnabled()) {
            return;
        }
        try {
            var keys = redis.keys("ind:%s:*".formatted(symbol));
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception e) {
            log.debug("Indicator cache evict skipped for {}: {}", symbol, e.getMessage());
        }
    }
}
