package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;
import np.com.thapanarayan.backend.marketdata.api.VolumeBinView;
import np.com.thapanarayan.backend.marketdata.api.VolumeNode;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;

/**
 * Builds a true volume-at-price profile from every trade in the window (§6.2):
 * bins the traded quantity by price, finds the Point of Control, expands the 70%
 * value area outward from it, and classifies high/low volume nodes.
 *
 * <p>Price binning uses {@code double} for the bin index (exact enough for price
 * bucketing and deterministic for fixed inputs); reported prices/widths stay
 * {@link BigDecimal} at the canonical 4-dp scale.</p>
 */
final class VolumeProfileBuilder {

    private static final int WIDTH_SCALE = 6;

    private VolumeProfileBuilder() {
    }

    static VolumeProfileView build(String symbol, java.time.LocalDate tradeDate,
            List<FloorsheetTradeRecord> trades, int requestedBins, double valueAreaFraction) {
        if (trades.isEmpty()) {
            throw new IllegalArgumentException("cannot build a volume profile from zero trades");
        }

        BigDecimal priceMin = trades.getFirst().price();
        BigDecimal priceMax = priceMin;
        long totalVolume = 0L;
        for (FloorsheetTradeRecord t : trades) {
            if (t.price().compareTo(priceMin) < 0) {
                priceMin = t.price();
            }
            if (t.price().compareTo(priceMax) > 0) {
                priceMax = t.price();
            }
            totalVolume += t.quantity();
        }

        double min = priceMin.doubleValue();
        double max = priceMax.doubleValue();

        // Degenerate range (every trade at one price): a single bin holds everything.
        if (priceMin.compareTo(priceMax) == 0) {
            VolumeBinView only = new VolumeBinView(
                    scale(priceMin), scale(priceMax), totalVolume, true, VolumeNode.NEUTRAL);
            return new VolumeProfileView(symbol, tradeDate, 1, BigDecimal.ZERO.setScale(WIDTH_SCALE),
                    scale(priceMin), scale(priceMax), scale(priceMin), scale(priceMax), scale(priceMin),
                    totalVolume, totalVolume, List.of(only));
        }

        int binCount = Math.max(1, requestedBins);
        double width = (max - min) / binCount;
        long[] vol = new long[binCount];
        for (FloorsheetTradeRecord t : trades) {
            int idx = (int) Math.floor((t.price().doubleValue() - min) / width);
            if (idx < 0) {
                idx = 0;
            } else if (idx >= binCount) {
                idx = binCount - 1; // priceMax lands exactly on the upper edge
            }
            vol[idx] += t.quantity();
        }

        int poc = 0;
        for (int i = 1; i < binCount; i++) {
            if (vol[i] > vol[poc]) {
                poc = i;
            }
        }

        // Expand the value area outward from the POC until it holds the target share.
        long target = (long) Math.ceil(valueAreaFraction * totalVolume);
        long vaVol = vol[poc];
        int lo = poc;
        int hi = poc;
        while (vaVol < target && (lo > 0 || hi < binCount - 1)) {
            long up = hi < binCount - 1 ? vol[hi + 1] : -1L;
            long down = lo > 0 ? vol[lo - 1] : -1L;
            if (up >= down) {
                hi++;
                vaVol += up;
            } else {
                lo--;
                vaVol += down;
            }
        }

        BigDecimal widthBd = BigDecimal.valueOf(width).setScale(WIDTH_SCALE, RoundingMode.HALF_UP);
        double mean = (double) totalVolume / binCount;
        double stddev = stddev(vol, mean);

        List<VolumeBinView> bins = new ArrayList<>(binCount);
        for (int i = 0; i < binCount; i++) {
            BigDecimal low = scale(BigDecimal.valueOf(min + i * width));
            BigDecimal high = i == binCount - 1 ? scale(priceMax) : scale(BigDecimal.valueOf(min + (i + 1) * width));
            bins.add(new VolumeBinView(low, high, vol[i], i >= lo && i <= hi, classify(vol, i, mean, stddev)));
        }

        return new VolumeProfileView(symbol, tradeDate, binCount, widthBd, scale(priceMin), scale(priceMax),
                bins.get(poc).priceLow().add(bins.get(poc).priceHigh()).divide(BigDecimal.valueOf(2),
                        OhlcvAggregate.PRICE_SCALE, RoundingMode.HALF_UP),
                bins.get(hi).priceHigh(), bins.get(lo).priceLow(), totalVolume, vaVol, bins);
    }

    private static VolumeNode classify(long[] vol, int i, double mean, double stddev) {
        if (vol[i] > mean + stddev) {
            return VolumeNode.HVN;
        }
        long left = i > 0 ? vol[i - 1] : Long.MAX_VALUE;
        long right = i < vol.length - 1 ? vol[i + 1] : Long.MAX_VALUE;
        if (vol[i] < left && vol[i] < right && vol[i] < mean) {
            return VolumeNode.LVN;
        }
        return VolumeNode.NEUTRAL;
    }

    private static double stddev(long[] vol, double mean) {
        double sumSq = 0.0;
        for (long v : vol) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / vol.length);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(OhlcvAggregate.PRICE_SCALE, RoundingMode.HALF_UP);
    }
}
