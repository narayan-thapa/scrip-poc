package np.com.thapanarayan.backend.indicator.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.indicator.api.IndicatorQuery;
import np.com.thapanarayan.backend.indicator.api.IndicatorSnapshotView;
import np.com.thapanarayan.backend.indicator.api.IndicatorsComputedEvent;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.platform.api.DomainEventPublisher;
import np.com.thapanarayan.backend.platform.api.NepseClock;

/**
 * Computes and persists the canonical indicator snapshot per (symbol, date), and
 * serves the published {@link IndicatorQuery}. Building each symbol's warm-up
 * {@link org.ta4j.core.BarSeries} from its candle history and reducing it through
 * {@link SnapshotCalculator}; recomputation merges on the natural key so a date
 * can be reprocessed idempotently.
 */
@Service
class IndicatorSnapshotService implements IndicatorQuery {

    private static final Logger log = LoggerFactory.getLogger(IndicatorSnapshotService.class);

    private final MarketDataQuery marketData;
    private final IndicatorSnapshotRepository snapshots;
    private final IndicatorSeriesCache seriesCache;
    private final DomainEventPublisher events;
    private final NepseClock clock;
    private final IndicatorProperties properties;

    IndicatorSnapshotService(MarketDataQuery marketData, IndicatorSnapshotRepository snapshots,
            IndicatorSeriesCache seriesCache, DomainEventPublisher events, NepseClock clock,
            IndicatorProperties properties) {
        this.marketData = marketData;
        this.snapshots = snapshots;
        this.seriesCache = seriesCache;
        this.events = events;
        this.clock = clock;
        this.properties = properties;
    }

    /**
     * Computes snapshots for every symbol that has market data on {@code date} and
     * publishes {@link IndicatorsComputedEvent}.
     *
     * @return number of symbols snapshotted
     */
    @Transactional
    public int computeForDate(LocalDate date, boolean suppressNotifications) {
        List<String> symbols = marketData.symbolsWithData(date);
        int count = 0;
        for (String symbol : symbols) {
            if (computeSnapshot(symbol, date).isPresent()) {
                count++;
            }
        }
        log.info("Computed indicator snapshots for {} symbols on {}", count, date);
        events.publish(new IndicatorsComputedEvent(date, count, suppressNotifications));
        return count;
    }

    /** Computes and persists a single (symbol, date) snapshot, if candles exist. */
    @Transactional
    public Optional<IndicatorSnapshotView> computeSnapshot(String symbol, LocalDate date) {
        List<DailyCandleView> candles = marketData.recentDailyCandles(symbol, date, properties.lookbackBars());
        if (candles.isEmpty()) {
            return Optional.empty();
        }
        IndicatorBarSeries series = BarSeriesAdapter.decimal(symbol, candles);
        SnapshotCalculator.Result r = SnapshotCalculator.compute(series.series());

        IndicatorSnapshotEntity e = snapshots.findBySymbolAndTradeDate(symbol, date)
                .orElseGet(IndicatorSnapshotEntity::new);
        e.setSymbol(symbol);
        e.setTradeDate(date);
        e.setBarCount(r.barCount());
        e.setRsi14(r.rsi14());
        e.setEma9(r.ema9());
        e.setEma21(r.ema21());
        e.setAtr14(r.atr14());
        e.setValues(r.values());
        e.setComputedAt(Instant.now(clock.clock()));
        IndicatorSnapshotView view = IndicatorMapper.toView(snapshots.save(e));
        seriesCache.evictSymbol(symbol); // explicit eviction on recompute (§10.12)
        return Optional.of(view);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IndicatorSnapshotView> snapshot(String symbol, LocalDate tradeDate) {
        return snapshots.findBySymbolAndTradeDate(symbol, tradeDate).map(IndicatorMapper::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IndicatorSnapshotView> latestSnapshot(String symbol) {
        return snapshots.findFirstBySymbolOrderByTradeDateDesc(symbol).map(IndicatorMapper::toView);
    }
}
