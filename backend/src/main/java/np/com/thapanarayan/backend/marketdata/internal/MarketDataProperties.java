package np.com.thapanarayan.backend.marketdata.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for market-data aggregation.
 *
 * @param volumeProfileBins   number of price bins in a daily volume profile (§6.2)
 * @param valueAreaFraction   value-area share of total volume (the "70% rule")
 * @param intradayIntervalMin intraday bucket width in minutes
 * @param intradayEnabled     whether to build/persist intraday candles during aggregation
 */
@ConfigurationProperties(prefix = "nepse.marketdata")
record MarketDataProperties(
        Integer volumeProfileBins,
        Double valueAreaFraction,
        Integer intradayIntervalMin,
        Boolean intradayEnabled) {

    MarketDataProperties {
        if (volumeProfileBins == null || volumeProfileBins < 1) {
            volumeProfileBins = 24;
        }
        if (valueAreaFraction == null || valueAreaFraction <= 0 || valueAreaFraction > 1) {
            valueAreaFraction = 0.70;
        }
        if (intradayIntervalMin == null || intradayIntervalMin < 1) {
            intradayIntervalMin = 15;
        }
        if (intradayEnabled == null) {
            intradayEnabled = true;
        }
    }
}
