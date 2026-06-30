package np.com.thapanarayan.backend.smc.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A structural break plotted as a labelled marker on the price chart.
 *
 * @param date  the bar whose close broke the prior swing level
 * @param price the swing level that was broken (where the marker sits)
 * @param label short display label, e.g. {@code "BOS"} or {@code "CHoCH"}
 */
public record SmcEvent(
        SmcEventType type,
        LocalDate date,
        BigDecimal price,
        String label) {
}
