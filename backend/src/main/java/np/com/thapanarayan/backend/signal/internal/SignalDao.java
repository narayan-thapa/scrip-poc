package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import np.com.thapanarayan.backend.signal.api.Reason;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class SignalDao {

    private static final String UPSERT = """
            INSERT INTO signal (id, symbol, trade_date, action, score, confidence, reasons, votes)
            VALUES (?,?,?,?,?,?, ?::jsonb, ?::jsonb)
            ON CONFLICT (symbol, trade_date) DO UPDATE SET
              action=EXCLUDED.action, score=EXCLUDED.score, confidence=EXCLUDED.confidence,
              reasons=EXCLUDED.reasons, votes=EXCLUDED.votes, generated_at=now()
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<SignalRecord> rowMapper;

    SignalDao(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rowMapper = (rs, n) -> new SignalRecord(
                rs.getObject("id", UUID.class),
                rs.getString("symbol"),
                rs.getObject("trade_date", LocalDate.class),
                SignalAction.valueOf(rs.getString("action")),
                rs.getDouble("score"),
                rs.getDouble("confidence"),
                List.of(mapper.readValue(rs.getString("reasons"), Reason[].class)),
                List.of(mapper.readValue(rs.getString("votes"), ConfluenceResult.VoteEntry[].class)),
                rs.getObject("generated_at", OffsetDateTime.class));
    }

    public void upsert(SignalRecord s) {
        jdbc.update(UPSERT, s.id(), s.symbol(), s.tradeDate(), s.action().name(), s.score(), s.confidence(),
                mapper.writeValueAsString(s.reasons()), mapper.writeValueAsString(s.votes()));
    }

    public Optional<LocalDate> latestDate() {
        return jdbc.query("SELECT max(trade_date) AS d FROM signal", (rs, n) -> rs.getObject("d", LocalDate.class))
                .stream().findFirst().filter(d -> d != null);
    }

    public List<SignalRecord> byDate(LocalDate date, SignalAction action, Double minScore) {
        StringBuilder sql = new StringBuilder("SELECT * FROM signal WHERE trade_date = ?");
        List<Object> args = new ArrayList<>();
        args.add(date);
        if (action != null) {
            sql.append(" AND action = ?");
            args.add(action.name());
        }
        if (minScore != null) {
            sql.append(" AND score >= ?");
            args.add(minScore);
        }
        sql.append(" ORDER BY score DESC");
        return jdbc.query(sql.toString(), rowMapper, args.toArray());
    }

    public Optional<SignalRecord> byId(UUID id) {
        return jdbc.query("SELECT * FROM signal WHERE id = ?", rowMapper, id).stream().findFirst();
    }

    public List<SignalRecord> bySymbol(String symbol, int limit) {
        return jdbc.query("SELECT * FROM signal WHERE symbol = ? ORDER BY trade_date DESC LIMIT ?",
                rowMapper, symbol, limit);
    }
}
