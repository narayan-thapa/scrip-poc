package np.com.thapanarayan.backend.indicator.internal;

import java.math.BigDecimal;

/**
 * One key/value pair as stored in the {@code indicator_snapshot.indicator_values}
 * JSONB array. Mirrors the published
 * {@link np.com.thapanarayan.backend.indicator.api.IndicatorValueView} but stays
 * internal as the Jackson-mapped persistence shape.
 */
record IndicatorValue(String key, BigDecimal value) {
}
