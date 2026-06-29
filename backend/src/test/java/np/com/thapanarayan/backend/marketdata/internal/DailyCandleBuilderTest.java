package np.com.thapanarayan.backend.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;

/** Daily OHLCV: open/close follow trade time (not input order), VWAP = turnover/volume. */
class DailyCandleBuilderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static FloorsheetTradeRecord trade(LocalTime time, double price, long qty, double amount) {
        return new FloorsheetTradeRecord("ABC", 1, 2, qty, BigDecimal.valueOf(price),
                BigDecimal.valueOf(amount), LocalDateTime.of(DATE, time), DATE);
    }

    @Test
    void buildsOhlcvFromTimeOrderedTrades() {
        // Deliberately out of time order to prove the builder sorts before reducing.
        List<FloorsheetTradeRecord> trades = List.of(
                trade(LocalTime.of(11, 2), 98, 5, 490),
                trade(LocalTime.of(11, 0), 100, 10, 1000),
                trade(LocalTime.of(11, 3), 102, 10, 1020),
                trade(LocalTime.of(11, 1), 105, 5, 525));

        OhlcvAggregate o = DailyCandleBuilder.build(trades);

        assertThat(o.open()).isEqualByComparingTo("100");
        assertThat(o.close()).isEqualByComparingTo("102");
        assertThat(o.high()).isEqualByComparingTo("105");
        assertThat(o.low()).isEqualByComparingTo("98");
        assertThat(o.volume()).isEqualTo(30);
        assertThat(o.turnover()).isEqualByComparingTo("3035");
        assertThat(o.vwap()).isEqualByComparingTo("101.1667"); // 3035 / 30
        assertThat(o.tradeCount()).isEqualTo(4);
    }
}
