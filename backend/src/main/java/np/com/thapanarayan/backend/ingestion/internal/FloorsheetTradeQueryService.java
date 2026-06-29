package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeQuery;
import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;

/**
 * JDBC read side of {@code floorsheet_trade}, mirroring the JDBC write path in
 * {@link FloorsheetTradeWriter}. Plain {@code JdbcTemplate} (not JPA) keeps these
 * read-only projections off the persistence context for the large row counts a
 * day's floorsheet carries.
 */
@Service
class FloorsheetTradeQueryService implements FloorsheetTradeQuery {

    private static final RowMapper<FloorsheetTradeRecord> MAPPER = (rs, rowNum) -> new FloorsheetTradeRecord(
            rs.getString("symbol"),
            rs.getInt("buyer_broker"),
            rs.getInt("seller_broker"),
            rs.getLong("quantity"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("amount"),
            rs.getTimestamp("trade_time").toLocalDateTime(),
            rs.getObject("trade_date", LocalDate.class));

    private static final String BY_DATE = """
            SELECT symbol, buyer_broker, seller_broker, quantity, price, amount, trade_time, trade_date
              FROM floorsheet_trade
             WHERE trade_date = ?
             ORDER BY trade_time, symbol
            """;

    private static final String BY_SYMBOL_RANGE = """
            SELECT symbol, buyer_broker, seller_broker, quantity, price, amount, trade_time, trade_date
              FROM floorsheet_trade
             WHERE symbol = ? AND trade_date BETWEEN ? AND ?
             ORDER BY trade_time
            """;

    private final JdbcTemplate jdbc;

    FloorsheetTradeQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FloorsheetTradeRecord> tradesForDate(LocalDate tradeDate) {
        return jdbc.query(BY_DATE, MAPPER, tradeDate);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FloorsheetTradeRecord> tradesForSymbolBetween(String symbol, LocalDate fromInclusive,
            LocalDate toInclusive) {
        return jdbc.query(BY_SYMBOL_RANGE, MAPPER, symbol, fromInclusive, toInclusive);
    }
}
