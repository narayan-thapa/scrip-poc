package np.com.thapanarayan.backend.screener.internal;

import java.util.List;

/**
 * Pure screener metrics (§6.6). Windows are counts over the trading-day candle series (daily_candle
 * only holds trading days, so counting rows == counting trading days). All methods are side-effect free.
 */
public final class ScreenerCalc {

    private ScreenerCalc() {
    }

    public record Rvol(double ratio, double zScore) {}

    /**
     * Relative volume vs the prior {@code N} sessions (today excluded from the baseline).
     * @param priorVolumes the N baseline volumes (most recent last); today's is separate
     */
    public static Rvol rvol(List<Long> priorVolumes, long volumeToday) {
        if (priorVolumes.isEmpty()) {
            return new Rvol(0, 0);
        }
        double mean = priorVolumes.stream().mapToLong(Long::longValue).average().orElse(0);
        double var = priorVolumes.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        double sd = Math.sqrt(var);
        double ratio = mean == 0 ? 0 : volumeToday / mean;
        double z = sd == 0 ? 0 : (volumeToday - mean) / sd;
        return new Rvol(round(ratio), round(z));
    }

    public record PriceDrop(double pctChange, double drawdownFromHigh, double sharpness) {}

    /**
     * The three drop lenses over a window (§6.6): point-to-point %Δ, drawdown from the window high,
     * and ATR-normalized sharpness ({@code %Δ / (ATR%·√N)}) — "sharp" meaning speed, not just size.
     *
     * @param closeNow     latest close
     * @param closeNAgo    close N sessions ago
     * @param windowHigh   highest high in the window
     * @param atrPct       ATR as a percent of price (e.g. 2.5 for 2.5%)
     * @param windowLength N (session count)
     */
    public static PriceDrop priceDrop(double closeNow, double closeNAgo, double windowHigh,
                                      double atrPct, int windowLength) {
        double pct = closeNAgo == 0 ? 0 : (closeNow - closeNAgo) / closeNAgo * 100;
        double dd = windowHigh == 0 ? 0 : (closeNow - windowHigh) / windowHigh * 100;
        double denom = atrPct * Math.sqrt(Math.max(1, windowLength));
        double sharpness = denom == 0 ? 0 : pct / denom;
        return new PriceDrop(round(pct), round(dd), round(sharpness));
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
