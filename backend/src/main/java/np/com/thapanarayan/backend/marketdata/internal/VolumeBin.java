package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;

import np.com.thapanarayan.backend.marketdata.api.VolumeNode;

/**
 * One price bin as stored in the {@code volume_profile.bins} JSONB array. Mirrors
 * {@link np.com.thapanarayan.backend.marketdata.api.VolumeBinView} but lives in
 * {@code internal} so it can be the Jackson-mapped persistence shape.
 */
record VolumeBin(
        BigDecimal priceLow,
        BigDecimal priceHigh,
        long volume,
        boolean inValueArea,
        VolumeNode node) {
}
