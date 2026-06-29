package np.com.thapanarayan.backend.indicator.api;

import java.math.BigDecimal;

/** A single named indicator value in a snapshot (e.g. {@code "ema9" -> 612.40}). */
public record IndicatorValueView(String key, BigDecimal value) {
}
