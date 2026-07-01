package np.com.thapanarayan.backend.indicator.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Indicator engine tunables: history window used to build a series for snapshot computation. */
@ConfigurationProperties(prefix = "indicator")
public record IndicatorProperties(Integer lookbackCalendarDays) {

    public IndicatorProperties {
        if (lookbackCalendarDays == null) {
            lookbackCalendarDays = 540; // ~ enough trading days for a 200-period EMA warm-up
        }
    }
}
