package np.com.thapanarayan.backend.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.ingestion.api.FloorsheetTradeRecord;
import np.com.thapanarayan.backend.marketdata.internal.IntradayCandleBuilder.IntradayBucket;

/** Trades fall into midnight-aligned interval buckets; each bucket is its own OHLCV. */
class IntradayCandleBuilderTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static FloorsheetTradeRecord trade(LocalTime time, double price, long qty) {
        BigDecimal p = BigDecimal.valueOf(price);
        return new FloorsheetTradeRecord("ABC", 1, 2, qty, p,
                p.multiply(BigDecimal.valueOf(qty)), LocalDateTime.of(DATE, time), DATE);
    }

    @Test
    void bucketsTradesIntoHourlyIntervals() {
        List<FloorsheetTradeRecord> trades = List.of(
                trade(LocalTime.of(11, 0, 30), 100, 10),
                trade(LocalTime.of(11, 45, 0), 102, 5),
                trade(LocalTime.of(12, 10, 0), 101, 8));

        List<IntradayBucket> buckets = IntradayCandleBuilder.build(DATE, trades, 60);

        assertThat(buckets).hasSize(2);

        IntradayBucket first = buckets.get(0);
        assertThat(first.bucketStart()).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(11, 0)));
        assertThat(first.ohlcv().open()).isEqualByComparingTo("100");
        assertThat(first.ohlcv().close()).isEqualByComparingTo("102");
        assertThat(first.ohlcv().volume()).isEqualTo(15);
        assertThat(first.ohlcv().tradeCount()).isEqualTo(2);

        IntradayBucket second = buckets.get(1);
        assertThat(second.bucketStart()).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(12, 0)));
        assertThat(second.ohlcv().volume()).isEqualTo(8);
        assertThat(second.ohlcv().tradeCount()).isEqualTo(1);
    }
}
