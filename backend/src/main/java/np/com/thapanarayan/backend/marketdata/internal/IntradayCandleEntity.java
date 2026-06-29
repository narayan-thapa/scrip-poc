package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** Persisted intraday OHLCV bucket. Recomputation deletes-then-inserts per (symbol, date). */
@Entity
@Table(name = "intraday_candle")
@IdClass(IntradayCandleId.class)
class IntradayCandleEntity {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Id
    @Column(name = "bucket_start", nullable = false)
    private LocalDateTime bucketStart;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(name = "open_price", nullable = false)
    private BigDecimal open;

    @Column(name = "high_price", nullable = false)
    private BigDecimal high;

    @Column(name = "low_price", nullable = false)
    private BigDecimal low;

    @Column(name = "close_price", nullable = false)
    private BigDecimal close;

    @Column(name = "volume", nullable = false)
    private long volume;

    @Column(name = "turnover", nullable = false)
    private BigDecimal turnover;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount;

    protected IntradayCandleEntity() {
    }

    String getSymbol() {
        return symbol;
    }

    void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    LocalDate getTradeDate() {
        return tradeDate;
    }

    void setTradeDate(LocalDate tradeDate) {
        this.tradeDate = tradeDate;
    }

    LocalDateTime getBucketStart() {
        return bucketStart;
    }

    void setBucketStart(LocalDateTime bucketStart) {
        this.bucketStart = bucketStart;
    }

    int getIntervalMinutes() {
        return intervalMinutes;
    }

    void setIntervalMinutes(int intervalMinutes) {
        this.intervalMinutes = intervalMinutes;
    }

    BigDecimal getOpen() {
        return open;
    }

    void setOpen(BigDecimal open) {
        this.open = open;
    }

    BigDecimal getHigh() {
        return high;
    }

    void setHigh(BigDecimal high) {
        this.high = high;
    }

    BigDecimal getLow() {
        return low;
    }

    void setLow(BigDecimal low) {
        this.low = low;
    }

    BigDecimal getClose() {
        return close;
    }

    void setClose(BigDecimal close) {
        this.close = close;
    }

    long getVolume() {
        return volume;
    }

    void setVolume(long volume) {
        this.volume = volume;
    }

    BigDecimal getTurnover() {
        return turnover;
    }

    void setTurnover(BigDecimal turnover) {
        this.turnover = turnover;
    }

    int getTradeCount() {
        return tradeCount;
    }

    void setTradeCount(int tradeCount) {
        this.tradeCount = tradeCount;
    }
}
