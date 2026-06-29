package np.com.thapanarayan.backend.indicator.api;

import java.util.List;

/**
 * Describes one indicator the engine can compute.
 *
 * @param key            stable identifier used on the API (e.g. {@code "rsi"}, {@code "macd"})
 * @param name           human-readable name
 * @param category       grouping for UI
 * @param defaultParams  default integer parameters (e.g. {@code [14]} for RSI)
 * @param lines          output line names ({@code ["rsi"]}, or {@code ["macd","signal"]})
 */
public record IndicatorCatalogEntry(
        String key,
        String name,
        IndicatorCategory category,
        List<Integer> defaultParams,
        List<String> lines) {
}
