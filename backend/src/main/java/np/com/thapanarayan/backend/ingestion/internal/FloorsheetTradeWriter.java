package np.com.thapanarayan.backend.ingestion.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Batch-upserts validated trades into {@code floorsheet_trade}. The
 * {@code ON CONFLICT … DO UPDATE} on the (contract_id, trade_date) key makes
 * re-ingesting a date idempotent — it replaces, never duplicates. Step-scoped so
 * it can stamp the originating {@code source_file_id}.
 */
class FloorsheetTradeWriter implements ItemWriter<ParsedTrade> {

    private static final String UPSERT = """
            INSERT INTO floorsheet_trade
                (contract_id, symbol, buyer_broker, seller_broker, quantity,
                 price, amount, trade_time, trade_date, source_file_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (contract_id, trade_date) DO UPDATE SET
                symbol = EXCLUDED.symbol,
                buyer_broker = EXCLUDED.buyer_broker,
                seller_broker = EXCLUDED.seller_broker,
                quantity = EXCLUDED.quantity,
                price = EXCLUDED.price,
                amount = EXCLUDED.amount,
                trade_time = EXCLUDED.trade_time,
                source_file_id = EXCLUDED.source_file_id,
                ingested_at = now()
            """;

    private final JdbcTemplate jdbc;
    private final Long jobId;

    FloorsheetTradeWriter(JdbcTemplate jdbc, Long jobId) {
        this.jdbc = jdbc;
        this.jobId = jobId;
    }

    @Override
    public void write(Chunk<? extends ParsedTrade> chunk) {
        var items = chunk.getItems();
        jdbc.batchUpdate(UPSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ParsedTrade t = items.get(i);
                ps.setString(1, t.contractId());
                ps.setString(2, t.symbol());
                ps.setInt(3, t.buyerBroker());
                ps.setInt(4, t.sellerBroker());
                ps.setLong(5, t.quantity());
                ps.setBigDecimal(6, t.price());
                ps.setBigDecimal(7, t.amount());
                ps.setTimestamp(8, Timestamp.valueOf(t.tradeTime()));
                ps.setObject(9, t.tradeDate());
                ps.setLong(10, jobId);
            }

            @Override
            public int getBatchSize() {
                return items.size();
            }
        });
    }
}
