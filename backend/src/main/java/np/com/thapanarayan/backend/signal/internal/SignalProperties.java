package np.com.thapanarayan.backend.signal.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the signal engine (§6.4).
 *
 * @param lookbackBars  trading-day history loaded per symbol to warm up the
 *                      strategies' indicators (longest is the 26+9 MACD / 200-ish EMA family)
 * @param buyThreshold  score at or above which the action is BUY ({@code +T_buy})
 * @param sellThreshold score at or below {@code -sellThreshold} which the action is SELL
 * @param maxTopReasons number of highest-magnitude reasons surfaced on the signal
 */
@ConfigurationProperties(prefix = "nepse.signal")
record SignalProperties(
        Integer lookbackBars,
        Double buyThreshold,
        Double sellThreshold,
        Integer maxTopReasons) {

    SignalProperties {
        if (lookbackBars == null || lookbackBars < 1) {
            lookbackBars = 260;
        }
        if (buyThreshold == null || buyThreshold <= 0) {
            buyThreshold = 35.0;
        }
        if (sellThreshold == null || sellThreshold <= 0) {
            sellThreshold = 35.0;
        }
        if (maxTopReasons == null || maxTopReasons < 1) {
            maxTopReasons = 3;
        }
    }
}
