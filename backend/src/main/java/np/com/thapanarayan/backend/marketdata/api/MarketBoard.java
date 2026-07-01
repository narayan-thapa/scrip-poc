package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Published board-wide reads for screeners/dashboard: all candles for a date + market summary. */
public interface MarketBoard {

    List<DailyCandleView> candlesOn(LocalDate date);

    Optional<MarketSummaryView> summary(LocalDate date);

    /** Most recent date with market data (dashboard default). */
    Optional<LocalDate> latestTradeDate();
}
