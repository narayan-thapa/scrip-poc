package np.com.thapanarayan.backend.smc.api;

import java.util.List;

/**
 * Smart Money Concepts analysis for one symbol over a date range: the detected
 * zones (order blocks, fair-value gaps) and structural-break events (BOS/CHoCH).
 *
 * @param swingLookback bars on each side used to confirm a swing point (fractal strength)
 * @param zones         order blocks and fair-value gaps, in detection order
 * @param events        BOS/CHoCH markers, ascending by date
 */
public record SmcView(
        String symbol,
        int swingLookback,
        List<SmcZone> zones,
        List<SmcEvent> events) {
}
