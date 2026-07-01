package np.com.thapanarayan.backend.charting.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;

/**
 * One composite payload for TradingView Lightweight Charts™ (F7): every array maps 1:1 to a chart
 * series. {@code time} is the business-day string (EOD → no intraday tick stream).
 */
public record ChartPayload(
        String symbol,
        String from,
        String to,
        List<Candle> candles,
        List<VolumePoint> volume,
        List<IndicatorOverlay> indicators,
        VolumeProfilePayload volumeProfile,
        List<MarkerPayload> markers) {

    public record Candle(String time, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {}

    public record VolumePoint(String time, long value, String color) {}

    /** An indicator overlay, tagged by output kind so the chart renders lines / signals / markers / zones. */
    public record IndicatorOverlay(
            String id,
            String outputKind,
            Map<String, List<IndicatorResult.Point>> lines,
            List<IndicatorResult.Point> plot,
            List<IndicatorResult.Event> events,
            List<IndicatorResult.Marker> markers,
            IndicatorResult.Zones zones) {}

    public record VolumeProfilePayload(BigDecimal poc, BigDecimal vah, BigDecimal val, List<Bin> bins) {
        public record Bin(BigDecimal price, long volume) {}
    }

    public record MarkerPayload(String id, String time, String action, double score) {}
}
