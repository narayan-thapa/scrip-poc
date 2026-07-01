package np.com.thapanarayan.backend.signal.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Confluence thresholds + the history window used to build each symbol's series. */
@ConfigurationProperties(prefix = "signal")
public record SignalProperties(Double buyThreshold, Double sellThreshold, Integer lookbackCalendarDays) {

    public SignalProperties {
        if (buyThreshold == null) {
            buyThreshold = 35.0;
        }
        if (sellThreshold == null) {
            sellThreshold = 35.0;
        }
        if (lookbackCalendarDays == null) {
            lookbackCalendarDays = 540;
        }
    }
}
