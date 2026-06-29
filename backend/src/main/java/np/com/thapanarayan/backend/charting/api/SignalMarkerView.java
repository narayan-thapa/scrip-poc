package np.com.thapanarayan.backend.charting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import np.com.thapanarayan.backend.signal.api.SignalAction;

/**
 * A signal plotted as a marker on the price chart: the action and score on a trade
 * date, linking back to the full signal by id so the UI can open its breakdown.
 */
public record SignalMarkerView(
        LocalDate date,
        SignalAction action,
        BigDecimal score,
        UUID signalId,
        String narrative) {
}
