package np.com.thapanarayan.backend.marketdata.internal;

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
 * Persisted volume profile (§6.2). Summary columns are promoted for cheap reads;
 * the full bin array is stored as JSONB and mapped via Jackson. Natural key
 * (symbol, trade_date) so recomputation merges in place.
 */
@Entity
@Table(name = "volume_profile")
@IdClass(VolumeProfileId.class)
class VolumeProfileEntity {

    @Id
    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "bin_count", nullable = false)
    private int binCount;

    @Column(name = "bin_width", nullable = false)
    private BigDecimal binWidth;

    @Column(name = "price_min", nullable = false)
    private BigDecimal priceMin;

    @Column(name = "price_max", nullable = false)
    private BigDecimal priceMax;

    @Column(name = "poc_price", nullable = false)
    private BigDecimal pocPrice;

    @Column(name = "value_area_high", nullable = false)
    private BigDecimal valueAreaHigh;

    @Column(name = "value_area_low", nullable = false)
    private BigDecimal valueAreaLow;

    @Column(name = "total_volume", nullable = false)
    private long totalVolume;

    @Column(name = "value_area_volume", nullable = false)
    private long valueAreaVolume;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bins", nullable = false, columnDefinition = "jsonb")
    private List<VolumeBin> bins;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected VolumeProfileEntity() {
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

    int getBinCount() {
        return binCount;
    }

    void setBinCount(int binCount) {
        this.binCount = binCount;
    }

    BigDecimal getBinWidth() {
        return binWidth;
    }

    void setBinWidth(BigDecimal binWidth) {
        this.binWidth = binWidth;
    }

    BigDecimal getPriceMin() {
        return priceMin;
    }

    void setPriceMin(BigDecimal priceMin) {
        this.priceMin = priceMin;
    }

    BigDecimal getPriceMax() {
        return priceMax;
    }

    void setPriceMax(BigDecimal priceMax) {
        this.priceMax = priceMax;
    }

    BigDecimal getPocPrice() {
        return pocPrice;
    }

    void setPocPrice(BigDecimal pocPrice) {
        this.pocPrice = pocPrice;
    }

    BigDecimal getValueAreaHigh() {
        return valueAreaHigh;
    }

    void setValueAreaHigh(BigDecimal valueAreaHigh) {
        this.valueAreaHigh = valueAreaHigh;
    }

    BigDecimal getValueAreaLow() {
        return valueAreaLow;
    }

    void setValueAreaLow(BigDecimal valueAreaLow) {
        this.valueAreaLow = valueAreaLow;
    }

    long getTotalVolume() {
        return totalVolume;
    }

    void setTotalVolume(long totalVolume) {
        this.totalVolume = totalVolume;
    }

    long getValueAreaVolume() {
        return valueAreaVolume;
    }

    void setValueAreaVolume(long valueAreaVolume) {
        this.valueAreaVolume = valueAreaVolume;
    }

    List<VolumeBin> getBins() {
        return bins;
    }

    void setBins(List<VolumeBin> bins) {
        this.bins = bins;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
