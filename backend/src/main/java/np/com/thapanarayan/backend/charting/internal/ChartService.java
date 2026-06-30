package np.com.thapanarayan.backend.charting.internal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.charting.api.ChartView;
import np.com.thapanarayan.backend.charting.api.SignalMarkerView;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesQuery;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.signal.api.SignalQuery;
import np.com.thapanarayan.backend.signal.api.SignalView;
import np.com.thapanarayan.backend.smc.api.SmcAnalysisQuery;
import np.com.thapanarayan.backend.smc.api.SmcView;

/**
 * Composes the composite chart payload (§10.8) by reading — never recomputing —
 * from market data (Stage 3), the indicator engine (Stage 4), and signals (Stage 5).
 * Also derives a content {@link #etag} so the controller can answer conditional GETs.
 */
@Service
class ChartService {

    private static final String VOLUME_PROFILE_OVERLAY = "volprofile";
    private static final String SMC_OVERLAY = "smc";

    private final MarketDataQuery marketData;
    private final IndicatorSeriesQuery indicators;
    private final SignalQuery signals;
    private final SmcAnalysisQuery smcAnalysis;

    ChartService(MarketDataQuery marketData, IndicatorSeriesQuery indicators, SignalQuery signals,
            SmcAnalysisQuery smcAnalysis) {
        this.marketData = marketData;
        this.indicators = indicators;
        this.signals = signals;
        this.smcAnalysis = smcAnalysis;
    }

    @Transactional(readOnly = true)
    public ChartView compose(String symbol, LocalDate from, LocalDate to,
            List<String> indicatorKeys, List<String> overlays) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new DomainException("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        String sym = symbol.trim().toUpperCase();

        List<DailyCandleView> candles = marketData.dailyCandles(sym, from, to);

        List<IndicatorSeriesView> overlaySeries = new ArrayList<>();
        if (indicatorKeys != null) {
            for (String key : indicatorKeys) {
                if (key != null && !key.isBlank()) {
                    overlaySeries.add(indicators.computeSeries(sym, key.trim(), null, from, to));
                }
            }
        }

        VolumeProfileView volumeProfile = null;
        if (wantsVolumeProfile(overlays) && !candles.isEmpty()) {
            LocalDate profileDate = candles.getLast().tradeDate();
            volumeProfile = marketData.volumeProfile(sym, profileDate).orElse(null);
        }

        List<SignalMarkerView> markers = signals.forSymbol(sym, from, to).stream()
                .map(ChartService::toMarker)
                .toList();

        SmcView smc = wantsOverlay(overlays, SMC_OVERLAY) ? smcAnalysis.analyze(sym, from, to) : null;

        return new ChartView(sym, from, to, candles, overlaySeries, volumeProfile, markers, smc);
    }

    /** A weak content tag: EOD data for a past range is immutable, so this is stable across requests. */
    String etag(ChartView view) {
        LocalDate lastCandle = view.candles().isEmpty() ? null : view.candles().getLast().tradeDate();
        LocalDate lastSignal = view.signals().isEmpty() ? null : view.signals().getLast().date();
        List<String> indicatorKeys = view.indicators().stream().map(IndicatorSeriesView::indicator).toList();
        SmcView smc = view.smc();
        int smcHash = smc == null ? 0 : Objects.hash(smc.zones().size(), smc.events().size());
        int hash = Objects.hash(view.symbol(), view.from(), view.to(),
                view.candles().size(), lastCandle, indicatorKeys,
                view.volumeProfile() != null, view.signals().size(), lastSignal, smcHash);
        return "W/\"" + Integer.toHexString(hash) + "\"";
    }

    private static boolean wantsVolumeProfile(List<String> overlays) {
        return wantsOverlay(overlays, VOLUME_PROFILE_OVERLAY);
    }

    private static boolean wantsOverlay(List<String> overlays, String name) {
        return overlays != null && overlays.stream()
                .anyMatch(o -> o != null && name.equalsIgnoreCase(o.trim()));
    }

    private static SignalMarkerView toMarker(SignalView s) {
        return new SignalMarkerView(s.tradeDate(), s.action(), s.score(), s.id(), s.narrative());
    }
}
