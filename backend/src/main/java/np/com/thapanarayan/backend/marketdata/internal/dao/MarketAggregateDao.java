package np.com.thapanarayan.backend.marketdata.internal.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.marketdata.internal.domain.MarketAggregateDaily;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarketAggregateDao {

    private static final RowMapper<MarketAggregateDaily> MAPPER = (rs, n) -> new MarketAggregateDaily(
            rs.getObject("trade_date", LocalDate.class),
            rs.getLong("total_volume"),
            rs.getBigDecimal("total_turnover"),
            rs.getLong("total_trades"),
            rs.getInt("advances"),
            rs.getInt("declines"),
            rs.getInt("unchanged"),
            rs.getBigDecimal("index_proxy_open"),
            rs.getBigDecimal("index_proxy_high"),
            rs.getBigDecimal("index_proxy_low"),
            rs.getBigDecimal("index_proxy_close"),
            rs.getBigDecimal("official_index_close"));

    private static final String UPSERT = """
            INSERT INTO market_aggregate_daily
              (trade_date, total_volume, total_turnover, total_trades, advances, declines, unchanged,
               index_proxy_open, index_proxy_high, index_proxy_low, index_proxy_close, official_index_close)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (trade_date) DO UPDATE SET
              total_volume=EXCLUDED.total_volume, total_turnover=EXCLUDED.total_turnover,
              total_trades=EXCLUDED.total_trades, advances=EXCLUDED.advances, declines=EXCLUDED.declines,
              unchanged=EXCLUDED.unchanged, index_proxy_open=EXCLUDED.index_proxy_open,
              index_proxy_high=EXCLUDED.index_proxy_high, index_proxy_low=EXCLUDED.index_proxy_low,
              index_proxy_close=EXCLUDED.index_proxy_close, official_index_close=EXCLUDED.official_index_close
            """;

    private final JdbcTemplate jdbc;

    MarketAggregateDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(MarketAggregateDaily a) {
        jdbc.update(UPSERT, a.tradeDate(), a.totalVolume(), a.totalTurnover(), a.totalTrades(),
                a.advances(), a.declines(), a.unchanged(), a.indexProxyOpen(), a.indexProxyHigh(),
                a.indexProxyLow(), a.indexProxyClose(), a.officialIndexClose());
    }

    public Optional<MarketAggregateDaily> find(LocalDate date) {
        return jdbc.query("SELECT * FROM market_aggregate_daily WHERE trade_date=?", MAPPER, date)
                .stream().findFirst();
    }

    public List<MarketAggregateDaily> findRange(LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM market_aggregate_daily WHERE trade_date BETWEEN ? AND ? "
                + "ORDER BY trade_date", MAPPER, from, to);
    }

    public Optional<LocalDate> latestDate() {
        return jdbc.query("SELECT max(trade_date) AS d FROM market_aggregate_daily",
                        (rs, n) -> rs.getObject("d", LocalDate.class))
                .stream().findFirst().filter(d -> d != null);
    }
}
