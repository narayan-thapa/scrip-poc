package np.com.thapanarayan.backend.backtest.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.BacktestRequest;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.EquityPoint;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.RunView;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.TradeLog;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class BacktestDao {

    private static final TypeReference<Map<String, Double>> METRICS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<RunView> runMapper;

    BacktestDao(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.runMapper = (rs, n) -> new RunView(
                rs.getString("id"), rs.getString("symbol"),
                rs.getObject("date_from", LocalDate.class).toString(),
                rs.getObject("date_to", LocalDate.class).toString(),
                rs.getDouble("starting_capital"), rs.getDouble("buy_threshold"),
                rs.getDouble("sell_threshold"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toString());
    }

    void saveRun(UUID id, BacktestRequest req, String status, String createdBy) {
        jdbc.update("""
                INSERT INTO backtest_run (id, symbol, date_from, date_to, starting_capital, buy_threshold,
                    sell_threshold, cost_model, status, created_by)
                VALUES (?,?,?,?,?,?,?, ?::jsonb, ?, ?)
                """, id, req.symbol(), req.from(), req.to(), req.startingCapital(), req.buyThreshold(),
                req.sellThreshold(), mapper.writeValueAsString(req.costConfig()), status, createdBy);
    }

    void updateStatus(UUID id, String status) {
        jdbc.update("UPDATE backtest_run SET status=? WHERE id=?", status, id);
    }

    void saveResult(UUID runId, Map<String, Double> metrics, List<EquityPoint> equity) {
        jdbc.update("INSERT INTO backtest_result (run_id, metrics, equity_curve) VALUES (?, ?::jsonb, ?::jsonb)",
                runId, mapper.writeValueAsString(metrics), mapper.writeValueAsString(equity));
    }

    void saveTrades(UUID runId, List<TradeLog> trades) {
        jdbc.batchUpdate("""
                INSERT INTO backtest_trade (run_id, entry_date, entry_price, exit_date, exit_price, quantity,
                    costs, pnl, return_pct, entry_reason, exit_reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TradeLog t = trades.get(i);
                ps.setObject(1, runId);
                ps.setObject(2, LocalDate.parse(t.entryDate()));
                ps.setDouble(3, t.entryPrice());
                ps.setObject(4, LocalDate.parse(t.exitDate()));
                ps.setDouble(5, t.exitPrice());
                ps.setLong(6, t.quantity());
                ps.setDouble(7, t.costs());
                ps.setDouble(8, t.pnl());
                ps.setDouble(9, t.returnPct());
                ps.setString(10, t.entryReason());
                ps.setString(11, t.exitReason());
            }

            @Override
            public int getBatchSize() {
                return trades.size();
            }
        });
    }

    public List<RunView> listRuns(int limit) {
        return jdbc.query("SELECT * FROM backtest_run ORDER BY created_at DESC LIMIT ?", runMapper, limit);
    }

    public Optional<RunView> findRun(UUID id) {
        return jdbc.query("SELECT * FROM backtest_run WHERE id=?", runMapper, id).stream().findFirst();
    }

    public Optional<Map<String, Double>> metrics(UUID id) {
        return jdbc.query("SELECT metrics FROM backtest_result WHERE run_id=?",
                (rs, n) -> mapper.readValue(rs.getString("metrics"), METRICS_TYPE), id).stream().findFirst();
    }

    public List<EquityPoint> equityCurve(UUID id) {
        return jdbc.query("SELECT equity_curve FROM backtest_result WHERE run_id=?",
                        (rs, n) -> List.of(mapper.readValue(rs.getString("equity_curve"), EquityPoint[].class)), id)
                .stream().findFirst().orElse(List.of());
    }

    public List<TradeLog> trades(UUID id) {
        return jdbc.query("SELECT * FROM backtest_trade WHERE run_id=? ORDER BY entry_date", (rs, n) -> new TradeLog(
                rs.getObject("entry_date", LocalDate.class).toString(), rs.getDouble("entry_price"),
                rs.getObject("exit_date", LocalDate.class).toString(), rs.getDouble("exit_price"),
                rs.getLong("quantity"), rs.getDouble("costs"), rs.getDouble("pnl"), rs.getDouble("return_pct"),
                rs.getString("entry_reason"), rs.getString("exit_reason")), id);
    }

    public boolean delete(UUID id) {
        return jdbc.update("DELETE FROM backtest_run WHERE id=?", id) > 0;
    }
}
