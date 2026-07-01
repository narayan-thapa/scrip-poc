package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;

/** Published after indicator snapshots are computed for a date. The signal engine (Phase 5) listens. */
public record IndicatorsComputedEvent(LocalDate tradeDate, boolean suppressNotifications) {
}
