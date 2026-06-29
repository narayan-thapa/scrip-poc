package np.com.thapanarayan.backend.marketdata.api;

import java.math.BigDecimal;

/**
 * One price bin of a volume profile.
 *
 * @param priceLow      inclusive lower price edge
 * @param priceHigh     exclusive upper price edge
 * @param volume        total traded quantity that fell in this bin
 * @param inValueArea   whether the bin lies within the 70% value area
 * @param node          HVN / LVN / NEUTRAL classification
 */
public record VolumeBinView(
        BigDecimal priceLow,
        BigDecimal priceHigh,
        long volume,
        boolean inValueArea,
        VolumeNode node) {
}
