package np.com.thapanarayan.backend.marketdata.internal.dao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.marketdata.internal.domain.DailyCandle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DailyCandleDao {

    private static final RowMapper<DailyCandle> MAPPER = (rs, n) -> new DailyCandle(
            rs.getString("symbol"),
            rs.getObject("trade_date", LocalDate.class),
            rs.getBigDecimal("open"),
            rs.getBigDecimal("high"),
            rs.getBigDecimal("low"),
            rs.getBigDecimal("close"),
            rs.getLong("volume"),
            rs.getBigDecimal("turnover"),
            rs.getInt("trades_count"),
            rs.getBigDecimal("vwap"),
            rs.getBigDecimal("prev_close"),
            rs.getBigDecimal("change_pct"));

    private static final String UPSERT = """
            INSERT INTO daily_candle
              (symbol, trade_date, open, high, low, close, volume, turnover, trades_count, vwap, prev_close, change_pct)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (symbol, trade_date) DO UPDATE SET
              open=EXCLUDED.open, high=EXCLUDED.high, low=EXCLUDED.low, close=EXCLUDED.close,
              volume=EXCLUDED.volume, turnover=EXCLUDED.turnover, trades_count=EXCLUDED.trades_count,
              vwap=EXCLUDED.vwap, prev_close=EXCLUDED.prev_close, change_pct=EXCLUDED.change_pct
            """;

    private final JdbcTemplate jdbc;

    DailyCandleDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(DailyCandle c) {
        jdbc.update(UPSERT, c.symbol(), c.tradeDate(), c.open(), c.high(), c.low(), c.close(),
                c.volume(), c.turnover(), c.tradesCount(), c.vwap(), c.prevClose(), c.changePct());
    }

    public List<DailyCandle> find(String symbol, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM daily_candle WHERE symbol=? AND trade_date BETWEEN ? AND ? "
                + "ORDER BY trade_date", MAPPER, symbol, from, to);
    }

    public Optional<BigDecimal> findClose(String symbol, LocalDate date) {
        return jdbc.query("SELECT close FROM daily_candle WHERE symbol=? AND trade_date=?",
                        (rs, n) -> rs.getBigDecimal("close"), symbol, date)
                .stream().findFirst();
    }

    public List<DailyCandle> listForDate(LocalDate date) {
        return jdbc.query("SELECT * FROM daily_candle WHERE trade_date=?", MAPPER, date);
    }
}
