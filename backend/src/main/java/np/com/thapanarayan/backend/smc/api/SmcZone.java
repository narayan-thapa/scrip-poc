package np.com.thapanarayan.backend.smc.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A rectangular price/time region: an order block or fair-value gap. The box spans
 * the price band {@code [bottom, top]} from {@code fromDate} to {@code toDate}.
 *
 * @param mitigated whether price has since traded back into the band (the zone has
 *                  been "filled"/tested) within the analysed range
 */
public record SmcZone(
        SmcZoneType type,
        LocalDate fromDate,
        LocalDate toDate,
        BigDecimal top,
        BigDecimal bottom,
        boolean mitigated) {
}
