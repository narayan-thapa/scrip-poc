package np.com.thapanarayan.backend.signal.internal.strategies;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** Shared helpers for strategy implementations. */
final class Strategies {

    private Strategies() {
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Indicator value at index, or NaN if undefined (warm-up). */
    static double val(Indicator<Num> indicator, int i) {
        Num v = indicator.getValue(i);
        return v.isNaN() ? Double.NaN : v.doubleValue();
    }

    static boolean anyNaN(double... values) {
        for (double v : values) {
            if (Double.isNaN(v)) {
                return true;
            }
        }
        return false;
    }

    static String r(double v) {
        return String.format("%.2f", v);
    }
}
