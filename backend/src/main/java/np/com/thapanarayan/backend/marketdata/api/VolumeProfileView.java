package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;

/** Published volume-profile levels for a (symbol, window) — consumed by the S4 strategy. */
public record VolumeProfileView(String symbol, BigDecimal poc, BigDecimal vah, BigDecimal val) {
}
