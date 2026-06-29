package np.com.thapanarayan.backend.marketdata.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Published read surface over derived market data. Downstream modules (indicator
 * engine, signal engine, charting) depend on this interface, never on the
 * marketdata {@code internal} package.
 */
public interface MarketDataQuery {

    /** Daily candles for a symbol across an inclusive date range, ascending by date. */
    List<DailyCandleView> dailyCandles(String symbol, LocalDate fromInclusive, LocalDate toInclusive);

    /**
     * The most recent {@code limit} daily candles for a symbol up to and including
     * {@code asOf}, returned ascending by date. Used to warm up look-back windows
     * (e.g. a 200-day moving average) for indicators.
     */
    List<DailyCandleView> recentDailyCandles(String symbol, LocalDate asOf, int limit);

    /** The latest daily candle for a symbol, if any has been aggregated. */
    Optional<DailyCandleView> latestCandle(String symbol);

    /** Symbols that have a daily candle on {@code tradeDate}, ascending. Drives per-symbol fan-out downstream. */
    List<String> symbolsWithData(LocalDate tradeDate);

    /** Volume profile for a (symbol, date), if aggregated. */
    Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate tradeDate);

    /** Broker flow for a (symbol, date), if aggregated. */
    Optional<BrokerFlowView> brokerFlow(String symbol, LocalDate tradeDate);
}
