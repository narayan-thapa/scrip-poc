package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;

import org.ta4j.core.BarSeries;

import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;

/**
 * Everything a {@link SignalStrategy} needs to evaluate one symbol as-of a date:
 * the Ta4j {@link BarSeries} (warm-up history ending at {@code tradeDate}) plus the
 * floorsheet-derived structures Ta4j has no concept of. Strategies evaluate at the
 * series end index ("now").
 *
 * <p>{@code volumeProfile} and {@code brokerFlow} are nullable — they exist only for
 * the as-of date and only when that day had trades; the custom strategies (S4, S8)
 * abstain when their structure is absent.</p>
 */
record SymbolContext(
        String symbol,
        LocalDate tradeDate,
        BarSeries series,
        VolumeProfileView volumeProfile,
        BrokerFlowView brokerFlow) {

    int endIndex() {
        return series.getEndIndex();
    }

    int barCount() {
        return series.getBarCount();
    }
}
