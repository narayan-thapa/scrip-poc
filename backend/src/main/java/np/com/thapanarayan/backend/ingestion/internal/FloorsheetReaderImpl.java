package np.com.thapanarayan.backend.ingestion.internal;

import java.time.LocalDate;
import java.util.List;
import np.com.thapanarayan.backend.ingestion.api.FloorsheetReader;
import np.com.thapanarayan.backend.ingestion.api.TradeView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
class FloorsheetReaderImpl implements FloorsheetReader {

    private static final RowMapper<TradeView> MAPPER = (rs, n) -> new TradeView(
            rs.getString("symbol"),
            rs.getInt("buyer_broker"),
            rs.getInt("seller_broker"),
            rs.getLong("quantity"),
            rs.getBigDecimal("price"),
            rs.getBigDecimal("amount"),
            rs.getObject("trade_time", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbc;

    FloorsheetReaderImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> symbolsTradedOn(LocalDate date) {
        return jdbc.queryForList(
                "SELECT DISTINCT symbol FROM floorsheet_trade WHERE trade_date = ? ORDER BY symbol",
                String.class, date);
    }

    @Override
    public List<TradeView> tradesForSymbolAndDate(String symbol, LocalDate date) {
        return jdbc.query(
                "SELECT symbol, buyer_broker, seller_broker, quantity, price, amount, trade_time "
                        + "FROM floorsheet_trade WHERE symbol = ? AND trade_date = ? ORDER BY trade_time",
                MAPPER, symbol, date);
    }

    @Override
    public Page<TradeView> page(String symbol, LocalDate date, Pageable pageable) {
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM floorsheet_trade WHERE symbol = ? AND trade_date = ?",
                Long.class, symbol, date);
        List<TradeView> content = jdbc.query(
                "SELECT symbol, buyer_broker, seller_broker, quantity, price, amount, trade_time "
                        + "FROM floorsheet_trade WHERE symbol = ? AND trade_date = ? "
                        + "ORDER BY trade_time LIMIT ? OFFSET ?",
                MAPPER, symbol, date, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }
}
