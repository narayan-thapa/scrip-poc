package np.com.thapanarayan.backend.marketdata.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Market-data tunables: default volume-profile bin count. */
@ConfigurationProperties(prefix = "marketdata")
public record MarketDataProperties(Integer volumeProfileBins) {

    public MarketDataProperties {
        if (volumeProfileBins == null) {
            volumeProfileBins = 24;
        }
    }
}
