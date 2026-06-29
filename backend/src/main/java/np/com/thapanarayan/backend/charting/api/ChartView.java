package np.com.thapanarayan.backend.charting.api;

import java.time.LocalDate;
import java.util.List;

import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;

/**
 * The composite price-chart payload (§10.8): everything the frontend needs to render
 * one scrip's chart in a single round trip — candles, the requested indicator
 * overlays, an optional volume-profile, and signal markers. Read-only composition
 * over Stages 3–5; no new computation of its own.
 *
 * @param candles        daily OHLCV over the range, ascending
 * @param indicators     requested overlay series (each with its named lines)
 * @param volumeProfile  value-area/POC profile for the range end, or {@code null} if not requested/available
 * @param signals        BUY/SELL/HOLD markers over the range
 */
public record ChartView(
        String symbol,
        LocalDate from,
        LocalDate to,
        List<DailyCandleView> candles,
        List<IndicatorSeriesView> indicators,
        VolumeProfileView volumeProfile,
        List<SignalMarkerView> signals) {
}
