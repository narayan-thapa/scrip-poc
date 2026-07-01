package np.com.thapanarayan.backend.marketdata.internal;

import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.marketdata.internal.dao.DailyCandleDao;
import org.springframework.stereotype.Service;

@Service
class CandleSeriesReaderImpl implements CandleSeriesReader {

    private final DailyCandleDao candles;

    CandleSeriesReaderImpl(DailyCandleDao candles) {
        this.candles = candles;
    }

    @Override
    public List<CandleBar> series(String symbol, LocalDate from, LocalDate to) {
        return candles.find(symbol, from, to).stream()
                .map(c -> new CandleBar(c.tradeDate(), c.open(), c.high(), c.low(), c.close(), c.volume()))
                .toList();
    }

    @Override
    public List<String> symbolsOn(LocalDate date) {
        return candles.listForDate(date).stream().map(c -> c.symbol()).toList();
    }
}
