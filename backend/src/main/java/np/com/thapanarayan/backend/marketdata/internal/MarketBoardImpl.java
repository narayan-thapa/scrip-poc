package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.marketdata.api.MarketBoard;
import np.com.thapanarayan.backend.marketdata.api.MarketSummaryView;
import np.com.thapanarayan.backend.marketdata.internal.dao.DailyCandleDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.MarketAggregateDao;
import org.springframework.stereotype.Service;

@Service
class MarketBoardImpl implements MarketBoard {

    private final DailyCandleDao candles;
    private final MarketAggregateDao aggregates;

    MarketBoardImpl(DailyCandleDao candles, MarketAggregateDao aggregates) {
        this.candles = candles;
        this.aggregates = aggregates;
    }

    @Override
    public List<DailyCandleView> candlesOn(LocalDate date) {
        return candles.listForDate(date).stream()
                .map(c -> new DailyCandleView(c.symbol(), c.tradeDate(), c.open(), c.high(), c.low(), c.close(),
                        c.volume(), c.turnover(), c.tradesCount(), c.changePct()))
                .toList();
    }

    @Override
    public Optional<MarketSummaryView> summary(LocalDate date) {
        return aggregates.find(date).map(a -> new MarketSummaryView(a.tradeDate(), a.advances(), a.declines(),
                a.unchanged(), a.totalVolume(), a.totalTurnover(), a.totalTrades()));
    }

    @Override
    public Optional<LocalDate> latestTradeDate() {
        return aggregates.latestDate();
    }
}
