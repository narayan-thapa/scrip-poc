package np.com.thapanarayan.backend.indicator.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Canonical per-(symbol, trade_date) indicator snapshot. A handful of close-based
 * indicators are promoted to columns for fast filtering; the full catalog is the
 * JSONB {@code indicator_values} array. Natural key so recomputation merges.
 */
@Entity
@Table(name = "indicator_snapshot")
@IdClass(IndicatorSnapshotId.class)
class IndicatorSnapshotEntity {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "bar_count", nullable = false)
    private int barCount;

    @Column(name = "rsi14")
    private BigDecimal rsi14;

    @Column(name = "ema9")
    private BigDecimal ema9;

    @Column(name = "ema21")
    private BigDecimal ema21;

    @Column(name = "atr14")
    private BigDecimal atr14;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "indicator_values", nullable = false, columnDefinition = "jsonb")
    private List<IndicatorValue> values;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    protected IndicatorSnapshotEntity() {
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

    int getBarCount() {
        return barCount;
    }

    void setBarCount(int barCount) {
        this.barCount = barCount;
    }

    BigDecimal getRsi14() {
        return rsi14;
    }

    void setRsi14(BigDecimal rsi14) {
        this.rsi14 = rsi14;
    }

    BigDecimal getEma9() {
        return ema9;
    }

    void setEma9(BigDecimal ema9) {
        this.ema9 = ema9;
    }

    BigDecimal getEma21() {
        return ema21;
    }

    void setEma21(BigDecimal ema21) {
        this.ema21 = ema21;
    }

    BigDecimal getAtr14() {
        return atr14;
    }

    void setAtr14(BigDecimal atr14) {
        this.atr14 = atr14;
    }

    List<IndicatorValue> getValues() {
        return values;
    }

    void setValues(List<IndicatorValue> values) {
        this.values = values;
    }

    Instant getComputedAt() {
        return computedAt;
    }

    void setComputedAt(Instant computedAt) {
        this.computedAt = computedAt;
    }
}
