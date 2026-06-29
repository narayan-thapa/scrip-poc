package np.com.thapanarayan.backend.indicator.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the indicator engine.
 *
 * @param lookbackBars    trading-day history loaded to warm up indicators (a
 *                        200-period EMA needs ~200 prior bars)
 * @param cacheEnabled    whether to use Redis for ad-hoc parametrized series
 * @param cacheTtlSeconds Redis entry TTL for cached series
 */
@ConfigurationProperties(prefix = "nepse.indicator")
record IndicatorProperties(
        Integer lookbackBars,
        Boolean cacheEnabled,
        Long cacheTtlSeconds) {

    IndicatorProperties {
        if (lookbackBars == null || lookbackBars < 1) {
            lookbackBars = 260;
        }
        if (cacheEnabled == null) {
            cacheEnabled = true;
        }
        if (cacheTtlSeconds == null || cacheTtlSeconds < 1) {
            cacheTtlSeconds = 3600L;
        }
    }
}
