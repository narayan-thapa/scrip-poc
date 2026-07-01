package np.com.thapanarayan.backend.marketdata.internal.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Exact volume-at-price profile over a window: POC/VAH/VAL + the binned histogram. */
public record VolumeProfile(
        String symbol,
        LocalDate windowFrom,
        LocalDate windowTo,
        BigDecimal poc,
        BigDecimal vah,
        BigDecimal val,
        List<Bin> bins) {

    /** A histogram bucket: representative {@code price} (bin center) and total {@code volume}. */
    public record Bin(BigDecimal price, long volume) {}
}
