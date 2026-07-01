package np.com.thapanarayan.backend.indicator.api;

import java.util.List;
import java.util.Map;

/**
 * Typed compute output, dispatched by {@link OutputKind}. The charting layer renders each variant
 * (lines / line+markers / markers / zone primitives) and the signal engine reads numeric votes.
 * Times are business-day strings ({@code YYYY-MM-DD}) to map 1:1 onto Lightweight Charts series.
 */
public sealed interface IndicatorResult
        permits IndicatorResult.Lines, IndicatorResult.Signals, IndicatorResult.Markers, IndicatorResult.Zones {

    /** {@code time}/{@code value} point; NaN warm-up points are omitted. */
    record Point(String time, double value) {}

    /** A discrete buy/sell event at a bar. */
    record Event(String time, String side, double price) {}

    /** A pattern/event marker on a bar. */
    record Marker(String time, String position, String shape, String color, String text) {}

    /** A rectangular zone (order block / FVG). */
    record Box(String fromTime, String toTime, double top, double bottom, String color, String label) {}

    /** A horizontal level/ray (liquidity, structure). */
    record Ray(String time, double price, String color, String label) {}

    /** A text label anchored at (time, price). */
    record Label(String time, double price, String text) {}

    /** LINE / BAND: one or more named series. */
    record Lines(Map<String, List<Point>> series) implements IndicatorResult {}

    /** SIGNAL: a plotted line plus buy/sell events. */
    record Signals(List<Point> plot, List<Event> events) implements IndicatorResult {}

    /** MARKER: discrete pattern markers. */
    record Markers(List<Marker> markers) implements IndicatorResult {}

    /** ZONE: boxes/rays/labels (SMC). */
    record Zones(List<Box> boxes, List<Ray> rays, List<Label> labels) implements IndicatorResult {}
}
