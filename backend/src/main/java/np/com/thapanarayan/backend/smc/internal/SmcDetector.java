package np.com.thapanarayan.backend.smc.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.smc.api.SmcEvent;
import np.com.thapanarayan.backend.smc.api.SmcEventType;
import np.com.thapanarayan.backend.smc.api.SmcView;
import np.com.thapanarayan.backend.smc.api.SmcZone;
import np.com.thapanarayan.backend.smc.api.SmcZoneType;

/**
 * Deterministic Smart Money Concepts detection over an ascending candle series.
 *
 * <p>The algorithm is a conventional, lookahead-free formulation:</p>
 * <ol>
 *   <li><b>Swing points</b> — a swing high at bar {@code i} is a close-strict
 *       fractal: its high is strictly greater than every high within
 *       {@code lookback} bars on each side (swing low symmetric). A swing is only
 *       <em>confirmed</em> {@code lookback} bars later, so it is never used as a
 *       reference level before it could actually be known.</li>
 *   <li><b>BOS / CHoCH</b> — walking forward, when a close breaks the most recent
 *       confirmed swing high it is a bullish break: a {@code BOS} if the trend was
 *       already up, otherwise a {@code CHoCH}. Bearish breaks of the swing low are
 *       symmetric. A broken level is consumed until the next swing confirms.</li>
 *   <li><b>Order blocks</b> — the last opposite-colour candle before the impulse
 *       that broke structure (last down candle before a bullish break, etc.).</li>
 *   <li><b>Fair-value gaps</b> — a three-candle imbalance: {@code low[i] > high[i-2]}
 *       (bullish) or {@code high[i] < low[i-2]} (bearish).</li>
 * </ol>
 *
 * <p>A zone is flagged {@code mitigated} when a later candle (after the zone's end)
 * trades back into its price band.</p>
 */
final class SmcDetector {

    /** How far back to scan for the order-block origin candle before a break. */
    private static final int ORDER_BLOCK_SCAN = 15;

    private SmcDetector() {
    }

    static SmcView analyze(String symbol, List<DailyCandleView> candles, int lookback) {
        List<SmcZone> zones = new ArrayList<>();
        List<SmcEvent> events = new ArrayList<>();
        int n = candles.size();
        if (n < 2 * lookback + 1) {
            return new SmcView(symbol, lookback, List.copyOf(zones), List.copyOf(events));
        }

        boolean[] swingHigh = new boolean[n];
        boolean[] swingLow = new boolean[n];
        for (int i = lookback; i < n - lookback; i++) {
            swingHigh[i] = isSwingHigh(candles, i, lookback);
            swingLow[i] = isSwingLow(candles, i, lookback);
        }

        // Most recent confirmed, not-yet-broken swing levels.
        BigDecimal refHigh = null;
        BigDecimal refLow = null;
        int trend = 0; // +1 up, -1 down, 0 unknown

        for (int j = 0; j < n; j++) {
            // Activate swings whose confirmation bar (i + lookback) is exactly j.
            int confirmed = j - lookback;
            if (confirmed >= 0) {
                if (swingHigh[confirmed]) {
                    refHigh = candles.get(confirmed).high();
                }
                if (swingLow[confirmed]) {
                    refLow = candles.get(confirmed).low();
                }
            }

            BigDecimal close = candles.get(j).close();
            if (refHigh != null && close.compareTo(refHigh) > 0) {
                SmcEventType type = trend < 0 ? SmcEventType.CHOCH_BULLISH : SmcEventType.BOS_BULLISH;
                events.add(new SmcEvent(type, candles.get(j).tradeDate(), refHigh, label(type)));
                addOrderBlock(zones, candles, j, true, n);
                trend = 1;
                refHigh = null; // consumed until the next swing high confirms
            } else if (refLow != null && close.compareTo(refLow) < 0) {
                SmcEventType type = trend > 0 ? SmcEventType.CHOCH_BEARISH : SmcEventType.BOS_BEARISH;
                events.add(new SmcEvent(type, candles.get(j).tradeDate(), refLow, label(type)));
                addOrderBlock(zones, candles, j, false, n);
                trend = -1;
                refLow = null;
            }
        }

        detectFairValueGaps(zones, candles, n);
        return new SmcView(symbol, lookback, List.copyOf(zones), List.copyOf(events));
    }

    /** The last opposite-colour candle before the break at {@code breakIdx} becomes the order block. */
    private static void addOrderBlock(List<SmcZone> zones, List<DailyCandleView> candles,
            int breakIdx, boolean bullish, int n) {
        int from = Math.max(0, breakIdx - ORDER_BLOCK_SCAN);
        for (int k = breakIdx - 1; k >= from; k--) {
            DailyCandleView c = candles.get(k);
            boolean down = c.close().compareTo(c.open()) < 0;
            boolean up = c.close().compareTo(c.open()) > 0;
            if (bullish && down || !bullish && up) {
                SmcZoneType type = bullish ? SmcZoneType.BULLISH_OB : SmcZoneType.BEARISH_OB;
                zones.add(new SmcZone(type, c.tradeDate(), c.tradeDate(), c.high(), c.low(),
                        mitigated(candles, k, c.high(), c.low(), n)));
                return;
            }
        }
    }

    private static void detectFairValueGaps(List<SmcZone> zones, List<DailyCandleView> candles, int n) {
        for (int i = 2; i < n; i++) {
            BigDecimal highPrev2 = candles.get(i - 2).high();
            BigDecimal lowPrev2 = candles.get(i - 2).low();
            BigDecimal lowCurr = candles.get(i).low();
            BigDecimal highCurr = candles.get(i).high();
            LocalDate fromDate = candles.get(i - 2).tradeDate();
            LocalDate toDate = candles.get(i).tradeDate();

            if (lowCurr.compareTo(highPrev2) > 0) {
                zones.add(new SmcZone(SmcZoneType.BULLISH_FVG, fromDate, toDate, lowCurr, highPrev2,
                        mitigated(candles, i, lowCurr, highPrev2, n)));
            } else if (highCurr.compareTo(lowPrev2) < 0) {
                zones.add(new SmcZone(SmcZoneType.BEARISH_FVG, fromDate, toDate, lowPrev2, highCurr,
                        mitigated(candles, i, lowPrev2, highCurr, n)));
            }
        }
    }

    /** A zone is mitigated when a later candle's range overlaps its price band. */
    private static boolean mitigated(List<DailyCandleView> candles, int endIdx,
            BigDecimal top, BigDecimal bottom, int n) {
        for (int k = endIdx + 1; k < n; k++) {
            DailyCandleView c = candles.get(k);
            if (c.low().compareTo(top) <= 0 && c.high().compareTo(bottom) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSwingHigh(List<DailyCandleView> candles, int i, int lookback) {
        BigDecimal h = candles.get(i).high();
        for (int k = i - lookback; k <= i + lookback; k++) {
            if (k != i && h.compareTo(candles.get(k).high()) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSwingLow(List<DailyCandleView> candles, int i, int lookback) {
        BigDecimal l = candles.get(i).low();
        for (int k = i - lookback; k <= i + lookback; k++) {
            if (k != i && l.compareTo(candles.get(k).low()) >= 0) {
                return false;
            }
        }
        return true;
    }

    private static String label(SmcEventType type) {
        return switch (type) {
            case BOS_BULLISH, BOS_BEARISH -> "BOS";
            case CHOCH_BULLISH, CHOCH_BEARISH -> "CHoCH";
        };
    }
}
