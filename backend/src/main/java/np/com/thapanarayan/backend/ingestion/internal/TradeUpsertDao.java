package np.com.thapanarayan.backend.ingestion.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import np.com.thapanarayan.backend.ingestion.internal.domain.FloorsheetTrade;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Bulk, idempotent persistence for {@link FloorsheetTrade}. Uses {@code INSERT ... ON CONFLICT
 * (contract_id, trade_date) DO UPDATE} so re-ingesting a date replaces rows instead of duplicating
 * (the contract_id natural key drives idempotency).
 */
@Repository
class TradeUpsertDao {

    private static final int IN_CHUNK = 1000;

    private static final String UPSERT = """
            INSERT INTO floorsheet_trade
                (contract_id, symbol, buyer_broker, seller_broker, quantity, price, amount,
                 trade_time, trade_date, source_file_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (contract_id, trade_date) DO UPDATE SET
                symbol = EXCLUDED.symbol, buyer_broker = EXCLUDED.buyer_broker,
                seller_broker = EXCLUDED.seller_broker, quantity = EXCLUDED.quantity,
                price = EXCLUDED.price, amount = EXCLUDED.amount, trade_time = EXCLUDED.trade_time,
                source_file_id = EXCLUDED.source_file_id, ingested_at = now()
            """;

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    TradeUpsertDao(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    /** Contract ids among {@code ids} that already exist for {@code date} (i.e. would be duplicates). */
    Set<String> existingContractIds(LocalDate date, Collection<String> ids) {
        Set<String> existing = new HashSet<>();
        List<String> all = new ArrayList<>(ids);
        for (int i = 0; i < all.size(); i += IN_CHUNK) {
            List<String> chunk = all.subList(i, Math.min(i + IN_CHUNK, all.size()));
            existing.addAll(namedJdbc.queryForList(
                    "SELECT contract_id FROM floorsheet_trade WHERE trade_date = :d AND contract_id IN (:ids)",
                    Map.of("d", date, "ids", chunk),
                    String.class));
        }
        return existing;
    }

    void upsertAll(List<FloorsheetTrade> trades) {
        jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                FloorsheetTrade t = trades.get(i);
                ps.setString(1, t.contractId());
                ps.setString(2, t.symbol());
                ps.setInt(3, t.buyerBroker());
                ps.setInt(4, t.sellerBroker());
                ps.setLong(5, t.quantity());
                ps.setBigDecimal(6, t.price());
                ps.setBigDecimal(7, t.amount());
                ps.setObject(8, t.tradeTime());
                ps.setObject(9, t.tradeDate());
                if (t.sourceFileId() != null) {
                    ps.setObject(10, t.sourceFileId());
                } else {
                    ps.setNull(10, Types.OTHER);
                }
            }

            @Override
            public int getBatchSize() {
                return trades.size();
            }
        });
    }

    long countByTradeDate(LocalDate date) {
        Long n = jdbc.queryForObject(
                "SELECT count(*) FROM floorsheet_trade WHERE trade_date = ?", Long.class, date);
        return n == null ? 0 : n;
    }
}
