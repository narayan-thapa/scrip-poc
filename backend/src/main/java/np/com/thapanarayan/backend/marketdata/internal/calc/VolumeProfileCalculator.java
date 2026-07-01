package np.com.thapanarayan.backend.marketdata.internal.calc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.marketdata.internal.domain.VolumeProfile.Bin;

/**
 * Exact volume-at-price profile (§6.2): bins Σ-quantity by price, finds the Point of Control, the
 * 70% Value Area (expanding from POC toward the heavier neighbor), and HVN/LVN nodes. Because we
 * have every trade this is a true profile, not a TPO approximation.
 */
public final class VolumeProfileCalculator {

    private static final double VALUE_AREA_FRACTION = 0.70;

    private VolumeProfileCalculator() {
    }

    public static VolumeProfileResult compute(List<TradeView> trades, int binCount) {
        if (trades.isEmpty()) {
            throw new IllegalArgumentException("no trades for volume profile");
        }
        int b = Math.max(1, binCount);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (TradeView t : trades) {
            double p = t.price().doubleValue();
            min = Math.min(min, p);
            max = Math.max(max, p);
        }

        // Degenerate range (all trades at one price): a single bin.
        if (max <= min) {
            long total = trades.stream().mapToLong(TradeView::quantity).sum();
            BigDecimal price = round(min);
            return new VolumeProfileResult(price, price, price,
                    List.of(new Bin(price, total)), List.of(price), List.of());
        }

        double width = (max - min) / b;
        long[] vol = new long[b];
        for (TradeView t : trades) {
            int idx = (int) Math.floor((t.price().doubleValue() - min) / width);
            if (idx >= b) {
                idx = b - 1; // price == max
            }
            if (idx < 0) {
                idx = 0;
            }
            vol[idx] += t.quantity();
        }

        List<Bin> bins = new ArrayList<>(b);
        for (int i = 0; i < b; i++) {
            double center = min + (i + 0.5) * width;
            bins.add(new Bin(round(center), vol[i]));
        }

        int poc = argMax(vol);
        long total = 0;
        for (long v : vol) {
            total += v;
        }

        // Expand the value area from the POC toward the heavier side until ≥70% of volume.
        int lo = poc;
        int hi = poc;
        long vaVol = vol[poc];
        double target = VALUE_AREA_FRACTION * total;
        while (vaVol < target) {
            long up = hi + 1 < b ? vol[hi + 1] : -1;
            long down = lo - 1 >= 0 ? vol[lo - 1] : -1;
            if (up < 0 && down < 0) {
                break;
            }
            if (up >= down) {
                hi++;
                vaVol += up;
            } else {
                lo--;
                vaVol += down;
            }
        }

        BigDecimal pocPrice = round(min + (poc + 0.5) * width);
        BigDecimal vah = round(min + (hi + 1) * width); // top of the highest value-area bin
        BigDecimal val = round(min + lo * width);       // bottom of the lowest value-area bin

        return new VolumeProfileResult(pocPrice, vah, val, bins, nodes(vol, bins, true), nodes(vol, bins, false));
    }

    private static int argMax(long[] vol) {
        int idx = 0;
        for (int i = 1; i < vol.length; i++) {
            if (vol[i] > vol[idx]) {
                idx = i;
            }
        }
        return idx;
    }

    /** HVN = bins above mean+σ; LVN = interior local minima (valleys). */
    private static List<BigDecimal> nodes(long[] vol, List<Bin> bins, boolean high) {
        int n = vol.length;
        double mean = 0;
        for (long v : vol) {
            mean += v;
        }
        mean /= n;
        double var = 0;
        for (long v : vol) {
            var += (v - mean) * (v - mean);
        }
        double sd = Math.sqrt(var / n);

        List<BigDecimal> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (high) {
                if (vol[i] > mean + sd) {
                    out.add(bins.get(i).price());
                }
            } else if (i > 0 && i < n - 1 && vol[i] < vol[i - 1] && vol[i] < vol[i + 1]) {
                out.add(bins.get(i).price());
            }
        }
        return out;
    }

    private static BigDecimal round(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
