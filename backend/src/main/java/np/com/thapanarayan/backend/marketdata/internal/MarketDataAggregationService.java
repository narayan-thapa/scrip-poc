package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeQuery;
import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;
import np.com.thapanarayan.backend.marketdata.api.BrokerNetView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;
import np.com.thapanarayan.backend.marketdata.internal.IntradayCandleBuilder.IntradayBucket;
import np.com.thapanarayan.backend.platform.api.DomainEventPublisher;
import np.com.thapanarayan.backend.marketdata.api.MarketDataReadyEvent;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.TradingCalendar;

/**
 * Derives all price/volume structure for a trade date from the raw floorsheet:
 * daily candle (with calendar-aware previous close), volume profile (§6.2),
 * per-broker flow (§6.3) and optional intraday buckets — one pass per symbol.
 *
 * <p>The whole date is aggregated in a single transaction so the
 * {@link MarketDataReadyEvent} fires only once every symbol has committed; that
 * keeps the downstream pipeline (indicators → signals) from racing partial data.
 * Recomputation is idempotent: candles/profiles merge on their natural key,
 * per-broker and intraday rows are deleted and re-inserted.</p>
 */
@Service
class MarketDataAggregationService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataAggregationService.class);
    private static final int CHANGE_SCALE = 4;

    private final FloorsheetTradeQuery trades;
    private final DailyCandleRepository candles;
    private final VolumeProfileRepository profiles;
    private final BrokerFlowDailyRepository brokerFlows;
    private final IntradayCandleRepository intraday;
    private final TradingCalendar calendar;
    private final NepseClock clock;
    private final DomainEventPublisher events;
    private final MarketDataProperties properties;

    MarketDataAggregationService(FloorsheetTradeQuery trades, DailyCandleRepository candles,
            VolumeProfileRepository profiles, BrokerFlowDailyRepository brokerFlows,
            IntradayCandleRepository intraday, TradingCalendar calendar, NepseClock clock,
            DomainEventPublisher events, MarketDataProperties properties) {
        this.trades = trades;
        this.candles = candles;
        this.profiles = profiles;
        this.brokerFlows = brokerFlows;
        this.intraday = intraday;
        this.calendar = calendar;
        this.clock = clock;
        this.events = events;
        this.properties = properties;
    }

    /**
     * Aggregates one trade date and publishes {@link MarketDataReadyEvent}.
     *
     * @return the number of symbols that produced market data
     */
    @Transactional
    public int aggregate(LocalDate date, boolean suppressNotifications) {
        List<FloorsheetTradeRecord> all = trades.tradesForDate(date);
        if (all.isEmpty()) {
            log.info("No trades to aggregate for {}", date);
            events.publish(new MarketDataReadyEvent(date, 0, suppressNotifications));
            return 0;
        }

        Map<String, List<FloorsheetTradeRecord>> bySymbol = new LinkedHashMap<>();
        for (FloorsheetTradeRecord t : all) {
            bySymbol.computeIfAbsent(t.symbol(), k -> new ArrayList<>()).add(t);
        }

        LocalDate previousTradingDay = previousTradingDayOrNull(date);
        Instant now = Instant.now(clock.clock());
        for (var entry : bySymbol.entrySet()) {
            aggregateSymbol(entry.getKey(), date, entry.getValue(), previousTradingDay, now);
        }

        int count = bySymbol.size();
        log.info("Aggregated market data for {} symbols on {}", count, date);
        events.publish(new MarketDataReadyEvent(date, count, suppressNotifications));
        return count;
    }

    private void aggregateSymbol(String symbol, LocalDate date, List<FloorsheetTradeRecord> symbolTrades,
            LocalDate previousTradingDay, Instant now) {
        saveDailyCandle(symbol, date, symbolTrades, previousTradingDay, now);
        saveVolumeProfile(symbol, date, symbolTrades, now);
        saveBrokerFlow(symbol, date, symbolTrades);
        if (properties.intradayEnabled()) {
            saveIntraday(symbol, date, symbolTrades);
        }
    }

    private void saveDailyCandle(String symbol, LocalDate date, List<FloorsheetTradeRecord> symbolTrades,
            LocalDate previousTradingDay, Instant now) {
        OhlcvAggregate ohlcv = DailyCandleBuilder.build(symbolTrades);
        BigDecimal previousClose = previousTradingDay == null
                ? null
                : candles.findBySymbolAndTradeDate(symbol, previousTradingDay)
                        .map(DailyCandleEntity::getClose)
                        .orElse(null);

        DailyCandleEntity e = candles.findBySymbolAndTradeDate(symbol, date).orElseGet(DailyCandleEntity::new);
        e.setSymbol(symbol);
        e.setTradeDate(date);
        e.setOpen(ohlcv.open());
        e.setHigh(ohlcv.high());
        e.setLow(ohlcv.low());
        e.setClose(ohlcv.close());
        e.setVolume(ohlcv.volume());
        e.setTurnover(ohlcv.turnover());
        e.setVwap(ohlcv.vwap());
        e.setPreviousClose(previousClose);
        e.setChangePercent(changePercent(ohlcv.close(), previousClose));
        e.setTradeCount(ohlcv.tradeCount());
        e.setCreatedAt(now);
        candles.save(e);
    }

    private void saveVolumeProfile(String symbol, LocalDate date, List<FloorsheetTradeRecord> symbolTrades,
            Instant now) {
        VolumeProfileView v = VolumeProfileBuilder.build(
                symbol, date, symbolTrades, properties.volumeProfileBins(), properties.valueAreaFraction());

        VolumeProfileEntity e = profiles.findBySymbolAndTradeDate(symbol, date).orElseGet(VolumeProfileEntity::new);
        e.setSymbol(symbol);
        e.setTradeDate(date);
        e.setBinCount(v.binCount());
        e.setBinWidth(v.binWidth());
        e.setPriceMin(v.priceMin());
        e.setPriceMax(v.priceMax());
        e.setPocPrice(v.poc());
        e.setValueAreaHigh(v.valueAreaHigh());
        e.setValueAreaLow(v.valueAreaLow());
        e.setTotalVolume(v.totalVolume());
        e.setValueAreaVolume(v.valueAreaVolume());
        e.setBins(v.bins().stream()
                .map(b -> new VolumeBin(b.priceLow(), b.priceHigh(), b.volume(), b.inValueArea(), b.node()))
                .toList());
        e.setCreatedAt(now);
        profiles.save(e);
    }

    private void saveBrokerFlow(String symbol, LocalDate date, List<FloorsheetTradeRecord> symbolTrades) {
        List<BrokerNetView> rows = BrokerFlowBuilder.netByBroker(symbolTrades);
        brokerFlows.deleteBySymbolAndTradeDate(symbol, date);
        List<BrokerFlowDailyEntity> entities = new ArrayList<>(rows.size());
        for (BrokerNetView r : rows) {
            BrokerFlowDailyEntity e = new BrokerFlowDailyEntity();
            e.setSymbol(symbol);
            e.setTradeDate(date);
            e.setBrokerId(r.brokerId());
            e.setBuyQty(r.buyQuantity());
            e.setSellQty(r.sellQuantity());
            e.setNetQty(r.netQuantity());
            e.setBuyAmount(r.buyAmount());
            e.setSellAmount(r.sellAmount());
            e.setNetAmount(r.netAmount());
            entities.add(e);
        }
        brokerFlows.saveAll(entities);
    }

    private void saveIntraday(String symbol, LocalDate date, List<FloorsheetTradeRecord> symbolTrades) {
        int interval = properties.intradayIntervalMin();
        List<IntradayBucket> buckets = IntradayCandleBuilder.build(date, symbolTrades, interval);
        intraday.deleteBySymbolAndTradeDate(symbol, date);
        List<IntradayCandleEntity> entities = new ArrayList<>(buckets.size());
        for (IntradayBucket bucket : buckets) {
            OhlcvAggregate o = bucket.ohlcv();
            IntradayCandleEntity e = new IntradayCandleEntity();
            e.setSymbol(symbol);
            e.setTradeDate(date);
            e.setBucketStart(bucket.bucketStart());
            e.setIntervalMinutes(interval);
            e.setOpen(o.open());
            e.setHigh(o.high());
            e.setLow(o.low());
            e.setClose(o.close());
            e.setVolume(o.volume());
            e.setTurnover(o.turnover());
            e.setTradeCount(o.tradeCount());
            entities.add(e);
        }
        intraday.saveAll(entities);
    }

    private BigDecimal changePercent(BigDecimal close, BigDecimal previousClose) {
        if (previousClose == null || previousClose.signum() == 0) {
            return null;
        }
        return close.subtract(previousClose)
                .divide(previousClose, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(CHANGE_SCALE, RoundingMode.HALF_UP);
    }

    private LocalDate previousTradingDayOrNull(LocalDate date) {
        try {
            return calendar.previousTradingDay(date);
        } catch (RuntimeException outOfCoverage) {
            log.debug("No previous trading day for {} ({}); previous close left null",
                    date, outOfCoverage.getMessage());
            return null;
        }
    }
}
