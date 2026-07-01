package np.com.thapanarayan.backend.indicator.internal.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Event;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Marker;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Point;
import np.com.thapanarayan.backend.indicator.internal.IndicatorSnapshot;

/** Request/response payloads for the indicator API. */
final class IndicatorDtos {

    private IndicatorDtos() {
    }

    record ComputeRequest(String symbol, String id, String from, String to, Map<String, Object> params) {}

    /** Tagged result so the frontend dispatches on {@code outputKind}; absent variants are null. */
    record ComputeResponse(
            String id,
            String outputKind,
            Map<String, List<Point>> lines,
            List<Point> plot,
            List<Event> events,
            List<Marker> markers,
            IndicatorResult.Zones zones) {

        static ComputeResponse from(String id, IndicatorResult result) {
            return switch (result) {
                case IndicatorResult.Lines l -> new ComputeResponse(id,
                        l.series().size() > 1 ? "BAND" : "LINE", l.series(), null, null, null, null);
                case IndicatorResult.Signals s -> new ComputeResponse(id, "SIGNAL", null, s.plot(), s.events(), null, null);
                case IndicatorResult.Markers m -> new ComputeResponse(id, "MARKER", null, null, null, m.markers(), null);
                case IndicatorResult.Zones z -> new ComputeResponse(id, "ZONE", null, null, null, null, z);
            };
        }
    }

    record CustomStateDto(IndicatorDescriptor descriptor, boolean enabled) {}

    record ToggleRequest(boolean enabled) {}

    record DefinitionRequest(String id, String name, String template, int fast, int slow) {}

    record SnapshotDto(String symbol, String tradeDate, Map<String, Double> values,
                       BigDecimal rsi14, BigDecimal ema9, BigDecimal ema21, BigDecimal atr14) {
        static SnapshotDto from(IndicatorSnapshot s) {
            return new SnapshotDto(s.symbol(), s.tradeDate().toString(), s.values(),
                    s.rsi14(), s.ema9(), s.ema21(), s.atr14());
        }
    }
}
