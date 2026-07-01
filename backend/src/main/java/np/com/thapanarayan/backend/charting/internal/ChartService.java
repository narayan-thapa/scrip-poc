package np.com.thapanarayan.backend.charting.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import np.com.thapanarayan.backend.charting.internal.ChartPayload.Candle;
import np.com.thapanarayan.backend.charting.internal.ChartPayload.IndicatorOverlay;
import np.com.thapanarayan.backend.charting.internal.ChartPayload.MarkerPayload;
import np.com.thapanarayan.backend.charting.internal.ChartPayload.VolumePoint;
import np.com.thapanarayan.backend.charting.internal.ChartPayload.VolumeProfilePayload;
import np.com.thapanarayan.backend.indicator.api.IndicatorEngine;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.marketdata.api.MarketAnalytics;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.signal.api.SignalReader;
import org.springframework.stereotype.Service;

/**
 * Read-only composition over marketdata + indicator + signal (F7): assembles candles, a volume
 * histogram, requested indicator overlays, the volume profile, and signal markers into one payload.
 */
@Service
public class ChartService {

    private final CandleSeriesReader candles;
    private final MarketAnalytics analytics;
    private final IndicatorEngine indicators;
    private final SignalReader signals;

    ChartService(CandleSeriesReader candles, MarketAnalytics analytics, IndicatorEngine indicators,
                 SignalReader signals) {
        this.candles = candles;
        this.analytics = analytics;
        this.indicators = indicators;
        this.signals = signals;
    }

    public ChartPayload chart(String symbol, LocalDate from, LocalDate to, List<String> indicatorIds,
                              Set<String> overlays) {
        List<CandleBar> bars = candles.series(symbol, from, to);
        if (bars.isEmpty()) {
            throw ApiException.notFound("No candles for " + symbol + " in " + from + ".." + to);
        }

        List<Candle> candlesticks = new ArrayList<>(bars.size());
        List<VolumePoint> volume = new ArrayList<>(bars.size());
        for (CandleBar c : bars) {
            String time = c.tradeDate().toString();
            candlesticks.add(new Candle(time, c.open(), c.high(), c.low(), c.close()));
            String color = c.close().compareTo(c.open()) >= 0 ? "rgba(22,163,74,0.4)" : "rgba(220,38,38,0.4)";
            volume.add(new VolumePoint(time, c.volume(), color));
        }

        List<IndicatorOverlay> overlaysOut = new ArrayList<>();
        for (String id : indicatorIds) {
            overlaysOut.add(toOverlay(id, indicators.compute(id, symbol, from, to, ParamValues.empty())));
        }

        VolumeProfilePayload profile = null;
        if (overlays.contains("volprofile")) {
            profile = analytics.volumeProfile(symbol, from, to)
                    .map(vp -> new VolumeProfilePayload(vp.poc(), vp.vah(), vp.val(),
                            vp.bins().stream()
                                    .map(b -> new VolumeProfilePayload.Bin(b.price(), b.volume()))
                                    .toList()))
                    .orElse(null);
        }

        return new ChartPayload(symbol, from.toString(), to.toString(), candlesticks, volume, overlaysOut,
                profile, markers(symbol, from, to));
    }

    public List<MarkerPayload> markers(String symbol, LocalDate from, LocalDate to) {
        return signals.markersFor(symbol, from, to).stream()
                .map(m -> new MarkerPayload(m.id(), m.tradeDate(), m.action().name(), m.score()))
                .toList();
    }

    /** A lightweight signature for the ETag — changes when the visible data or request would change. */
    public String signature(ChartPayload p) {
        String lastCandle = p.candles().isEmpty() ? "-" : p.candles().get(p.candles().size() - 1).time();
        return "%s:%s:%s:%d:%s:%d:%d".formatted(p.symbol(), p.from(), p.to(),
                p.candles().size(), lastCandle, p.indicators().size(), p.markers().size());
    }

    private IndicatorOverlay toOverlay(String id, IndicatorResult result) {
        return switch (result) {
            case IndicatorResult.Lines l ->
                    new IndicatorOverlay(id, l.series().size() > 1 ? "BAND" : "LINE", l.series(), null, null, null, null);
            case IndicatorResult.Signals s ->
                    new IndicatorOverlay(id, "SIGNAL", null, s.plot(), s.events(), null, null);
            case IndicatorResult.Markers m ->
                    new IndicatorOverlay(id, "MARKER", null, null, null, m.markers(), null);
            case IndicatorResult.Zones z ->
                    new IndicatorOverlay(id, "ZONE", null, null, null, null, z);
        };
    }
}
