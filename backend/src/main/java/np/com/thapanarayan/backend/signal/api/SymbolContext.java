package np.com.thapanarayan.backend.signal.api;

import java.time.LocalDate;
import java.util.Optional;
import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import org.ta4j.core.BarSeries;

/**
 * Everything a strategy needs to vote on one symbol: the Ta4j {@link BarSeries}, the bar
 * {@link #index()} to evaluate at, and the floorsheet-native analytics (volume profile + broker
 * flow). For the live daily signal the index is the last bar; for a backtest the confluence-score
 * indicator sweeps every index.
 */
public record SymbolContext(
        String symbol,
        LocalDate date,
        BarSeries series,
        int index,
        Optional<VolumeProfileView> volumeProfile,
        Optional<BrokerFlowView> brokerFlow) {

    /** Context evaluating at the latest bar (the live daily-signal path). */
    public static SymbolContext atEnd(String symbol, LocalDate date, BarSeries series,
                                      Optional<VolumeProfileView> volumeProfile,
                                      Optional<BrokerFlowView> brokerFlow) {
        return new SymbolContext(symbol, date, series, series.getEndIndex(), volumeProfile, brokerFlow);
    }

    /** Context evaluating at a specific bar (backtest sweep); analytics are per-bar-unaware here. */
    public static SymbolContext at(String symbol, BarSeries series, int index) {
        return new SymbolContext(symbol, null, series, index, Optional.empty(), Optional.empty());
    }
}
