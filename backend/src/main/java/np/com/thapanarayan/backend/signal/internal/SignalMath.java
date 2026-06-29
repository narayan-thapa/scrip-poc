package np.com.thapanarayan.backend.signal.internal;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/** Small numeric helpers shared by the strategies and the scorer. */
final class SignalMath {

    private SignalMath() {
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Squash a raw ratio into a saturating [0, 1] confidence (≈0.76 at ratio=1). */
    static double saturate(double ratio) {
        double r = Math.abs(ratio);
        return clamp(r / (r + 0.3), 0.0, 1.0);
    }

    /** Reads an indicator value, mapping NaN / warm-up / errors to {@code 0.0}. */
    static double safe(Indicator<Num> indicator, int index) {
        try {
            Num n = indicator.getValue(index);
            if (n == null || n.isNaN()) {
                return 0.0;
            }
            return n.doubleValue();
        } catch (RuntimeException notAvailable) {
            return 0.0;
        }
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
