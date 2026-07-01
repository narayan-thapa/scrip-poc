package np.com.thapanarayan.backend.marketdata.internal.calc;

import java.math.BigDecimal;
import java.util.List;
import np.com.thapanarayan.backend.marketdata.internal.domain.VolumeProfile.Bin;

/** Output of {@link VolumeProfileCalculator}: control point, value area, histogram and nodes. */
public record VolumeProfileResult(
        BigDecimal poc,
        BigDecimal vah,
        BigDecimal val,
        List<Bin> bins,
        List<BigDecimal> highVolumeNodes,
        List<BigDecimal> lowVolumeNodes) {
}
