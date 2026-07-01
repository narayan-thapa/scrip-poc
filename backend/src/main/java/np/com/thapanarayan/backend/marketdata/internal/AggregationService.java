package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.FloorsheetReader;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import np.com.thapanarayan.backend.ingestion.api.TradesIngestedEvent;
import np.com.thapanarayan.backend.marketdata.api.MarketDataReadyEvent;
import np.com.thapanarayan.backend.marketdata.internal.calc.BrokerFlowCalculator;
import np.com.thapanarayan.backend.marketdata.internal.calc.BrokerFlowResult;
import np.com.thapanarayan.backend.marketdata.internal.calc.CandleCalculator;
import np.com.thapanarayan.backend.marketdata.internal.calc.VolumeProfileCalculator;
import np.com.thapanarayan.backend.marketdata.internal.calc.VolumeProfileResult;
import np.com.thapanarayan.backend.marketdata.internal.dao.BrokerFlowDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.DailyCandleDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.MarketAggregateDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.VolumeProfileDao;
import np.com.thapanarayan.backend.marketdata.internal.domain.BrokerFlow;
import np.com.thapanarayan.backend.marketdata.internal.domain.DailyCandle;
import np.com.thapanarayan.backend.marketdata.internal.domain.MarketAggregateDaily;
import np.com.thapanarayan.backend.marketdata.internal.domain.VolumeProfile;
import np.com.thapanarayan.backend.reference.api.InstrumentDirectory;
import np.com.thapanarayan.backend.reference.api.TradingCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Derives all market data for a trading day: per-symbol daily candle (with calendar-aware prev close),
 * exact volume profile, and broker flow; then the whole-market {@code NEPSE} aggregate (totals,
 * breadth, optional cap-weighted index proxy). Runs after trades are committed and emits
 * {@link MarketDataReadyEvent} for the indicator engine.
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);
    private static final String NEPSE = "NEPSE";

    private final FloorsheetReader trades;
    private final TradingCalendar calendar;
    private final InstrumentDirectory instruments;
    private final DailyCandleDao candleDao;
    private final VolumeProfileDao volumeProfileDao;
    private final BrokerFlowDao brokerFlowDao;
    private final MarketAggregateDao aggregateDao;
    private final ApplicationEventPublisher events;
    private final MarketDataProperties props;

    AggregationService(FloorsheetReader trades, TradingCalendar calendar, InstrumentDirectory instruments,
                       DailyCandleDao candleDao, VolumeProfileDao volumeProfileDao, BrokerFlowDao brokerFlowDao,
                       MarketAggregateDao aggregateDao, ApplicationEventPublisher events, MarketDataProperties props) {
        this.trades = trades;
        this.calendar = calendar;
        this.instruments = instruments;
        this.candleDao = candleDao;
        this.volumeProfileDao = volumeProfileDao;
        this.brokerFlowDao = brokerFlowDao;
        this.aggregateDao = aggregateDao;
        this.events = events;
        this.props = props;
    }

    /** Fires after the ingest transaction commits, so all trades for the date are visible. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional
    public void onTradesIngested(TradesIngestedEvent e) {
        doAggregate(e.tradeDate(), e.suppressNotifications());
    }

    /** Direct entry point (manual trigger / tests). */
    @Transactional
    public void aggregate(LocalDate date, boolean suppressNotifications) {
        doAggregate(date, suppressNotifications);
    }

    private void doAggregate(LocalDate date, boolean suppressNotifications) {
        LocalDate prevDay = calendar.previousTradingDay(date).orElse(null);
        int bins = props.volumeProfileBins();

        for (String symbol : trades.symbolsTradedOn(date)) {
            if (NEPSE.equals(symbol)) {
                continue; // the index is derived, never ingested as a scrip
            }
            List<TradeView> dayTrades = trades.tradesForSymbolAndDate(symbol, date);
            if (dayTrades.isEmpty()) {
                continue;
            }
            BigDecimal prevClose = prevDay == null ? null : candleDao.findClose(symbol, prevDay).orElse(null);
            candleDao.upsert(CandleCalculator.compute(symbol, date, dayTrades, prevClose));

            VolumeProfileResult vp = VolumeProfileCalculator.compute(dayTrades, bins);
            volumeProfileDao.upsert(new VolumeProfile(symbol, date, date, vp.poc(), vp.vah(), vp.val(), vp.bins()));

            brokerFlowDao.upsertAll(toBrokerFlowRows(symbol, date, BrokerFlowCalculator.compute(dayTrades)));
        }

        aggregateDao.upsert(buildMarketAggregate(date));
        log.info("Market data ready for {}", date);
        events.publishEvent(new MarketDataReadyEvent(date, suppressNotifications));
    }

    private List<BrokerFlow> toBrokerFlowRows(String symbol, LocalDate date, BrokerFlowResult flow) {
        List<BrokerFlow> rows = new ArrayList<>(flow.brokers().size());
        for (var b : flow.brokers()) {
            rows.add(new BrokerFlow(symbol, date, b.brokerId(), b.buyQty(), b.sellQty(), b.netQty(),
                    b.buyAmount(), b.sellAmount()));
        }
        return rows;
    }

    private MarketAggregateDaily buildMarketAggregate(LocalDate date) {
        List<DailyCandle> candles = candleDao.listForDate(date);
        long totalVolume = 0;
        long totalTrades = 0;
        BigDecimal totalTurnover = BigDecimal.ZERO;
        int advances = 0;
        int declines = 0;
        int unchanged = 0;

        boolean haveShares = false;
        BigDecimal proxyOpen = BigDecimal.ZERO;
        BigDecimal proxyHigh = BigDecimal.ZERO;
        BigDecimal proxyLow = BigDecimal.ZERO;
        BigDecimal proxyClose = BigDecimal.ZERO;

        for (DailyCandle c : candles) {
            totalVolume += c.volume();
            totalTrades += c.tradesCount();
            totalTurnover = totalTurnover.add(c.turnover());
            int dir = c.changePct() == null ? 0 : c.changePct().signum();
            if (dir > 0) {
                advances++;
            } else if (dir < 0) {
                declines++;
            } else {
                unchanged++;
            }
            var shares = instruments.listedShares(c.symbol());
            if (shares.isPresent() && shares.get() > 0) {
                haveShares = true;
                BigDecimal s = BigDecimal.valueOf(shares.get());
                proxyOpen = proxyOpen.add(c.open().multiply(s));
                proxyHigh = proxyHigh.add(c.high().multiply(s));
                proxyLow = proxyLow.add(c.low().multiply(s));
                proxyClose = proxyClose.add(c.close().multiply(s));
            }
        }

        return new MarketAggregateDaily(date, totalVolume, totalTurnover, totalTrades, advances, declines, unchanged,
                haveShares ? proxyOpen : null, haveShares ? proxyHigh : null,
                haveShares ? proxyLow : null, haveShares ? proxyClose : null, null);
    }
}
