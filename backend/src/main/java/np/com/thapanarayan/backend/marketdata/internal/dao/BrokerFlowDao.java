package np.com.thapanarayan.backend.marketdata.internal.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.marketdata.internal.domain.BrokerFlow;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BrokerFlowDao {

    private static final RowMapper<BrokerFlow> MAPPER = (rs, n) -> new BrokerFlow(
            rs.getString("symbol"),
            rs.getObject("trade_date", LocalDate.class),
            rs.getInt("broker_id"),
            rs.getLong("buy_qty"),
            rs.getLong("sell_qty"),
            rs.getLong("net_qty"),
            rs.getBigDecimal("buy_amount"),
            rs.getBigDecimal("sell_amount"));

    private static final String UPSERT = """
            INSERT INTO broker_flow_daily
              (symbol, trade_date, broker_id, buy_qty, sell_qty, net_qty, buy_amount, sell_amount)
            VALUES (?,?,?,?,?,?,?,?)
            ON CONFLICT (symbol, trade_date, broker_id) DO UPDATE SET
              buy_qty=EXCLUDED.buy_qty, sell_qty=EXCLUDED.sell_qty, net_qty=EXCLUDED.net_qty,
              buy_amount=EXCLUDED.buy_amount, sell_amount=EXCLUDED.sell_amount
            """;

    private final JdbcTemplate jdbc;

    BrokerFlowDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertAll(List<BrokerFlow> rows) {
        jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                BrokerFlow f = rows.get(i);
                ps.setString(1, f.symbol());
                ps.setObject(2, f.tradeDate());
                ps.setInt(3, f.brokerId());
                ps.setLong(4, f.buyQty());
                ps.setLong(5, f.sellQty());
                ps.setLong(6, f.netQty());
                ps.setBigDecimal(7, f.buyAmount());
                ps.setBigDecimal(8, f.sellAmount());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    /** Per-broker rows for a symbol+date, biggest net buyers first. */
    public List<BrokerFlow> find(String symbol, LocalDate date) {
        return jdbc.query("SELECT * FROM broker_flow_daily WHERE symbol=? AND trade_date=? "
                + "ORDER BY net_qty DESC", MAPPER, symbol, date);
    }

    /** Market-wide net per broker for a date (net buyers first) — top accumulators/distributors. */
    public List<BrokerNet> netByBrokerForDate(LocalDate date) {
        return jdbc.query("""
                SELECT broker_id, sum(buy_qty) AS buy_qty, sum(sell_qty) AS sell_qty, sum(net_qty) AS net_qty
                FROM broker_flow_daily WHERE trade_date=? GROUP BY broker_id ORDER BY net_qty DESC
                """,
                (rs, n) -> new BrokerNet(rs.getInt("broker_id"), rs.getLong("buy_qty"),
                        rs.getLong("sell_qty"), rs.getLong("net_qty")),
                date);
    }

    /** Aggregate net flow for one broker across all symbols on a date. */
    public record BrokerNet(int brokerId, long buyQty, long sellQty, long netQty) {}
}
