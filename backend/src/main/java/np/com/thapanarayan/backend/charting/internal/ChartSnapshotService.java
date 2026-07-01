package np.com.thapanarayan.backend.charting.internal;

import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.signal.api.SignalMarker;
import np.com.thapanarayan.backend.signal.api.SignalReader;
import org.springframework.stereotype.Service;

/** Produces a server-side PNG chart snapshot (candles + volume + signal markers) for a symbol/range. */
@Service
public class ChartSnapshotService {

    private final CandleSeriesReader candles;
    private final SignalReader signals;

    ChartSnapshotService(CandleSeriesReader candles, SignalReader signals) {
        this.candles = candles;
        this.signals = signals;
    }

    public byte[] snapshotPng(String symbol, LocalDate from, LocalDate to, int width, int height) {
        List<CandleBar> bars = candles.series(symbol, from, to);
        List<SignalMarker> markers = signals.markersFor(symbol, from, to);
        return ChartSnapshotRenderer.renderPng(symbol, bars, markers, width, height);
    }
}
