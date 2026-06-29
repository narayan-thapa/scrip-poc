package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Published read port over persisted indicator snapshots. The signal engine and
 * charting read indicators through this, never the {@code internal} package.
 */
public interface IndicatorQuery {

    Optional<IndicatorSnapshotView> snapshot(String symbol, LocalDate tradeDate);

    Optional<IndicatorSnapshotView> latestSnapshot(String symbol);
}
