package np.com.thapanarayan.backend.indicator.api;

import java.util.List;
import java.util.Map;

/**
 * An ad-hoc computed indicator series over a date range. Multi-output indicators
 * (MACD, Bollinger) return several named lines; single-output indicators return
 * one line keyed by the indicator's key.
 *
 * @param lines line name → ordered points (ascending by date)
 */
public record IndicatorSeriesView(
        String symbol,
        String indicator,
        List<Integer> params,
        Map<String, List<IndicatorPoint>> lines) {
}
