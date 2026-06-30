package np.com.thapanarayan.backend.smc.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.smc.api.SmcAnalysisQuery;
import np.com.thapanarayan.backend.smc.api.SmcView;

/**
 * Computes Smart Money Concepts structure over the requested range. Read-only over
 * {@link MarketDataQuery}; the heavy lifting lives in {@link SmcDetector}.
 */
@Service
class SmcService implements SmcAnalysisQuery {

    /** Fractal strength: bars required on each side to confirm a swing point. */
    private static final int SWING_LOOKBACK = 2;

    private final MarketDataQuery marketData;

    SmcService(MarketDataQuery marketData) {
        this.marketData = marketData;
    }

    @Override
    @Transactional(readOnly = true)
    public SmcView analyze(String symbol, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new DomainException("INVALID_RANGE", "'from' must be on or before 'to'");
        }
        String sym = symbol.trim().toUpperCase();
        List<DailyCandleView> candles = marketData.dailyCandles(sym, from, to);
        return SmcDetector.analyze(sym, candles, SWING_LOOKBACK);
    }
}
