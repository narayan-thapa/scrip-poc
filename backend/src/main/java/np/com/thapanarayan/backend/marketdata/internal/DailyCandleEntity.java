package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Persisted daily OHLCV candle. Natural composite key (symbol, trade_date): one
 * row per scrip per day, so {@code save} merges on recomputation — re-aggregating
 * a date replaces the prior candle rather than duplicating it.
 */
@Entity
@Table(name = "daily_candle")
@IdClass(DailyCandleId.class)
class DailyCandleEntity {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

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

    @Column(name = "vwap", nullable = false)
    private BigDecimal vwap;

    @Column(name = "previous_close")
    private BigDecimal previousClose;

    @Column(name = "change_percent")
    private BigDecimal changePercent;

    @Column(name = "trade_count", nullable = false)
    private int tradeCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DailyCandleEntity() {
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

    BigDecimal getVwap() {
        return vwap;
    }

    void setVwap(BigDecimal vwap) {
        this.vwap = vwap;
    }

    BigDecimal getPreviousClose() {
        return previousClose;
    }

    void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    BigDecimal getChangePercent() {
        return changePercent;
    }

    void setChangePercent(BigDecimal changePercent) {
        this.changePercent = changePercent;
    }

    int getTradeCount() {
        return tradeCount;
    }

    void setTradeCount(int tradeCount) {
        this.tradeCount = tradeCount;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
