package np.com.thapanarayan.backend.marketdata.internal;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Composite identity for {@link IntradayCandleEntity}: (symbol, trade_date,
 * bucket_start). trade_date is part of the key because the table is range
 * partitioned on it.
 */
class IntradayCandleId implements Serializable {

    private String symbol;
    private LocalDate tradeDate;
    private LocalDateTime bucketStart;

    IntradayCandleId() {
    }

    IntradayCandleId(String symbol, LocalDate tradeDate, LocalDateTime bucketStart) {
        this.symbol = symbol;
        this.tradeDate = tradeDate;
        this.bucketStart = bucketStart;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntradayCandleId that)) {
            return false;
        }
        return Objects.equals(symbol, that.symbol)
                && Objects.equals(tradeDate, that.tradeDate)
                && Objects.equals(bucketStart, that.bucketStart);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, tradeDate, bucketStart);
    }
}
