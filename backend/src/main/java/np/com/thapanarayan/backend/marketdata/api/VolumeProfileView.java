package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * True volume-at-price profile for a (symbol, trade_date), per §6.2.
 *
 * @param poc             Point of Control — price of the highest-volume bin
 * @param valueAreaHigh   upper edge of the 70% value area (VAH)
 * @param valueAreaLow    lower edge of the 70% value area (VAL)
 * @param bins            full ascending bin array (overlays for charting)
 */
public record VolumeProfileView(
        String symbol,
        LocalDate tradeDate,
        int binCount,
        BigDecimal binWidth,
        BigDecimal priceMin,
        BigDecimal priceMax,
        BigDecimal poc,
        BigDecimal valueAreaHigh,
        BigDecimal valueAreaLow,
        long totalVolume,
        long valueAreaVolume,
        List<VolumeBinView> bins) {
}
