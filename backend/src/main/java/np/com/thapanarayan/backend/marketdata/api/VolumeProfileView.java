package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.util.List;

/** Published volume profile: control point + value area + the histogram bins (for charting/S4). */
public record VolumeProfileView(String symbol, BigDecimal poc, BigDecimal vah, BigDecimal val, List<PriceBin> bins) {

    /** One histogram bucket: representative price + total volume. */
    public record PriceBin(BigDecimal price, long volume) {}
}
