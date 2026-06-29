package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.marketdata.api.BrokerFlowView;
import np.com.thapanarayan.backend.marketdata.api.BrokerNetView;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.IntradayCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketDataQuery;
import np.com.thapanarayan.backend.marketdata.api.MarketSummaryView;
import np.com.thapanarayan.backend.marketdata.api.MoverType;
import np.com.thapanarayan.backend.marketdata.api.MoverView;
import np.com.thapanarayan.backend.marketdata.api.VolumeProfileView;

/**
 * Read side of the marketdata module: serves the published {@link MarketDataQuery}
 * port and the controller's movers/summary queries. Broker-flow concentration is
 * recomputed from the persisted per-broker rows (see {@link BrokerFlowBuilder}),
 * so it always matches what was stored without a redundant summary table.
 */
@Service
@Transactional(readOnly = true)
class MarketDataQueryService implements MarketDataQuery {

    private final DailyCandleRepository candles;
    private final VolumeProfileRepository profiles;
    private final BrokerFlowDailyRepository brokerFlows;
    private final IntradayCandleRepository intraday;

    MarketDataQueryService(DailyCandleRepository candles, VolumeProfileRepository profiles,
            BrokerFlowDailyRepository brokerFlows, IntradayCandleRepository intraday) {
        this.candles = candles;
        this.profiles = profiles;
        this.brokerFlows = brokerFlows;
        this.intraday = intraday;
    }

    @Override
    public List<DailyCandleView> dailyCandles(String symbol, LocalDate fromInclusive, LocalDate toInclusive) {
        return candles.findBySymbolAndTradeDateBetweenOrderByTradeDateAsc(symbol, fromInclusive, toInclusive)
                .stream().map(MarketDataMapper::toView).toList();
    }

    @Override
    public List<DailyCandleView> recentDailyCandles(String symbol, LocalDate asOf, int limit) {
        List<DailyCandleEntity> newestFirst = candles
                .findBySymbolAndTradeDateLessThanEqualOrderByTradeDateDesc(symbol, asOf, Limit.of(Math.max(1, limit)));
        List<DailyCandleView> ascending = new ArrayList<>(newestFirst.size());
        for (int i = newestFirst.size() - 1; i >= 0; i--) {
            ascending.add(MarketDataMapper.toView(newestFirst.get(i)));
        }
        return ascending;
    }

    @Override
    public Optional<DailyCandleView> latestCandle(String symbol) {
        return candles.findFirstBySymbolOrderByTradeDateDesc(symbol).map(MarketDataMapper::toView);
    }

    @Override
    public List<String> symbolsWithData(LocalDate tradeDate) {
        return candles.findSymbolsByTradeDate(tradeDate);
    }

    @Override
    public Optional<VolumeProfileView> volumeProfile(String symbol, LocalDate tradeDate) {
        return profiles.findBySymbolAndTradeDate(symbol, tradeDate).map(MarketDataMapper::toView);
    }

    @Override
    public Optional<BrokerFlowView> brokerFlow(String symbol, LocalDate tradeDate) {
        List<BrokerFlowDailyEntity> rows = brokerFlows.findBySymbolAndTradeDateOrderByNetQtyDesc(symbol, tradeDate);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        List<BrokerNetView> nets = rows.stream().map(MarketDataMapper::toView).toList();
        return Optional.of(BrokerFlowBuilder.summarize(symbol, tradeDate, nets));
    }

    // ---- Controller-facing queries (not part of the published port) ----------

    public List<IntradayCandleView> intradayCandles(String symbol, LocalDate tradeDate) {
        return intraday.findBySymbolAndTradeDateOrderByBucketStartAsc(symbol, tradeDate)
                .stream().map(MarketDataMapper::toView).toList();
    }

    public List<MoverView> movers(LocalDate tradeDate, MoverType type, int limit) {
        List<DailyCandleEntity> day = candles.findByTradeDate(tradeDate);
        Comparator<DailyCandleEntity> order = switch (type) {
            case GAINERS -> Comparator.comparing(MarketDataQueryService::changeOrZero).reversed();
            case LOSERS -> Comparator.comparing(MarketDataQueryService::changeOrZero);
            case ACTIVE -> Comparator.comparing(DailyCandleEntity::getTurnover).reversed();
        };
        return day.stream()
                .filter(c -> type == MoverType.ACTIVE || c.getChangePercent() != null)
                .sorted(order)
                .limit(Math.max(1, limit))
                .map(c -> new MoverView(c.getSymbol(), c.getClose(), c.getPreviousClose(),
                        c.getChangePercent(), c.getVolume(), c.getTurnover()))
                .toList();
    }

    public MarketSummaryView summary(LocalDate tradeDate) {
        List<DailyCandleEntity> day = candles.findByTradeDate(tradeDate);
        int advances = 0;
        int declines = 0;
        int unchanged = 0;
        long totalVolume = 0L;
        BigDecimal totalTurnover = BigDecimal.ZERO;
        long totalTrades = 0L;
        for (DailyCandleEntity c : day) {
            BigDecimal change = c.getChangePercent();
            if (change == null || change.signum() == 0) {
                unchanged++;
            } else if (change.signum() > 0) {
                advances++;
            } else {
                declines++;
            }
            totalVolume += c.getVolume();
            totalTurnover = totalTurnover.add(c.getTurnover());
            totalTrades += c.getTradeCount();
        }
        return new MarketSummaryView(tradeDate, day.size(), advances, declines, unchanged,
                totalVolume, totalTurnover, totalTrades);
    }

    private static BigDecimal changeOrZero(DailyCandleEntity c) {
        return c.getChangePercent() == null ? BigDecimal.ZERO : c.getChangePercent();
    }
}
