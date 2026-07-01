package np.com.thapanarayan.backend.indicator.internal;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class IndicatorSnapshotDao {

    private static final TypeReference<Map<String, Double>> MAP_TYPE = new TypeReference<>() {};

    private static final String UPSERT = """
            INSERT INTO indicator_snapshot (symbol, trade_date, values, rsi14, ema9, ema21, atr14)
            VALUES (?,?, ?::jsonb, ?,?,?,?)
            ON CONFLICT (symbol, trade_date) DO UPDATE SET
              values=EXCLUDED.values, rsi14=EXCLUDED.rsi14, ema9=EXCLUDED.ema9,
              ema21=EXCLUDED.ema21, atr14=EXCLUDED.atr14
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<IndicatorSnapshot> rowMapper;

    IndicatorSnapshotDao(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rowMapper = (rs, n) -> new IndicatorSnapshot(
                rs.getString("symbol"),
                rs.getObject("trade_date", LocalDate.class),
                mapper.readValue(rs.getString("values"), MAP_TYPE),
                rs.getBigDecimal("rsi14"),
                rs.getBigDecimal("ema9"),
                rs.getBigDecimal("ema21"),
                rs.getBigDecimal("atr14"));
    }

    public void upsert(IndicatorSnapshot s) {
        jdbc.update(UPSERT, s.symbol(), s.tradeDate(), mapper.writeValueAsString(s.values()),
                s.rsi14(), s.ema9(), s.ema21(), s.atr14());
    }

    public Optional<IndicatorSnapshot> findLatest(String symbol) {
        return jdbc.query("SELECT * FROM indicator_snapshot WHERE symbol=? ORDER BY trade_date DESC LIMIT 1",
                rowMapper, symbol).stream().findFirst();
    }

    public Optional<IndicatorSnapshot> find(String symbol, LocalDate date) {
        return jdbc.query("SELECT * FROM indicator_snapshot WHERE symbol=? AND trade_date=?",
                rowMapper, symbol, date).stream().findFirst();
    }
}
