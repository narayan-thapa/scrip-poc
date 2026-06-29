package np.com.thapanarayan.backend.indicator.internal;

import java.util.List;

import np.com.thapanarayan.backend.indicator.api.IndicatorSnapshotView;
import np.com.thapanarayan.backend.indicator.api.IndicatorValueView;

/** Entity → published-view conversion for indicator snapshots. */
final class IndicatorMapper {

    private IndicatorMapper() {
    }

    static IndicatorSnapshotView toView(IndicatorSnapshotEntity e) {
        List<IndicatorValueView> values = e.getValues().stream()
                .map(v -> new IndicatorValueView(v.key(), v.value()))
                .toList();
        return new IndicatorSnapshotView(
                e.getSymbol(), e.getTradeDate(), e.getBarCount(),
                e.getRsi14(), e.getEma9(), e.getEma21(), e.getAtr14(), values);
    }
}
