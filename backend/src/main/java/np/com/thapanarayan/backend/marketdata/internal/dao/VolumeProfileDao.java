package np.com.thapanarayan.backend.marketdata.internal.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import np.com.thapanarayan.backend.marketdata.internal.domain.VolumeProfile;
import np.com.thapanarayan.backend.marketdata.internal.domain.VolumeProfile.Bin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Persists the volume profile; the histogram bins are stored as JSONB. (Jackson 3 = tools.jackson.) */
@Repository
public class VolumeProfileDao {

    private static final String UPSERT = """
            INSERT INTO volume_profile (symbol, window_from, window_to, poc, vah, val, bins)
            VALUES (?,?,?,?,?,?, ?::jsonb)
            ON CONFLICT (symbol, window_from, window_to) DO UPDATE SET
              poc=EXCLUDED.poc, vah=EXCLUDED.vah, val=EXCLUDED.val, bins=EXCLUDED.bins
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<VolumeProfile> rowMapper;

    VolumeProfileDao(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rowMapper = (rs, n) -> new VolumeProfile(
                rs.getString("symbol"),
                rs.getObject("window_from", LocalDate.class),
                rs.getObject("window_to", LocalDate.class),
                rs.getBigDecimal("poc"),
                rs.getBigDecimal("vah"),
                rs.getBigDecimal("val"),
                List.of(mapper.readValue(rs.getString("bins"), Bin[].class)));
    }

    public void upsert(VolumeProfile p) {
        jdbc.update(UPSERT, p.symbol(), p.windowFrom(), p.windowTo(), p.poc(), p.vah(), p.val(),
                mapper.writeValueAsString(p.bins()));
    }

    public Optional<VolumeProfile> find(String symbol, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT * FROM volume_profile WHERE symbol=? AND window_from=? AND window_to=?",
                rowMapper, symbol, from, to).stream().findFirst();
    }
}
