package np.com.thapanarayan.backend.screener.internal;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Screener defaults: baseline window, RVOL spike/drop thresholds, liquidity floor, drop presets. */
@ConfigurationProperties(prefix = "screener")
public record ScreenerProperties(
        Integer baselineWindow,
        Double spikeThreshold,
        Double dropThreshold,
        Long minTurnover,
        Integer priceDropWindow,
        List<Integer> dropPresets) {

    public ScreenerProperties {
        if (baselineWindow == null) {
            baselineWindow = 20;
        }
        if (spikeThreshold == null) {
            spikeThreshold = 2.0;
        }
        if (dropThreshold == null) {
            dropThreshold = 0.5;
        }
        if (minTurnover == null) {
            minTurnover = 100_000L; // liquidity floor
        }
        if (priceDropWindow == null) {
            priceDropWindow = 30;
        }
        if (dropPresets == null || dropPresets.isEmpty()) {
            dropPresets = List.of(30, 45, 60);
        }
    }
}
