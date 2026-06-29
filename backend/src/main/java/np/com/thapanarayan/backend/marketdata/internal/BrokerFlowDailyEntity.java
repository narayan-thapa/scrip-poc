package np.com.thapanarayan.backend.marketdata.internal;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One broker's net flow for a (symbol, trade_date). Surrogate id with a unique
 * (symbol, trade_date, broker_id) constraint; recomputation deletes the date's
 * rows then re-inserts, so the per-broker set always reflects the latest trades.
 */
@Entity
@Table(name = "broker_flow_daily")
class BrokerFlowDailyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "broker_id", nullable = false)
    private int brokerId;

    @Column(name = "buy_qty", nullable = false)
    private long buyQty;

    @Column(name = "sell_qty", nullable = false)
    private long sellQty;

    @Column(name = "net_qty", nullable = false)
    private long netQty;

    @Column(name = "buy_amount", nullable = false)
    private BigDecimal buyAmount;

    @Column(name = "sell_amount", nullable = false)
    private BigDecimal sellAmount;

    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    protected BrokerFlowDailyEntity() {
    }

    Long getId() {
        return id;
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

    int getBrokerId() {
        return brokerId;
    }

    void setBrokerId(int brokerId) {
        this.brokerId = brokerId;
    }

    long getBuyQty() {
        return buyQty;
    }

    void setBuyQty(long buyQty) {
        this.buyQty = buyQty;
    }

    long getSellQty() {
        return sellQty;
    }

    void setSellQty(long sellQty) {
        this.sellQty = sellQty;
    }

    long getNetQty() {
        return netQty;
    }

    void setNetQty(long netQty) {
        this.netQty = netQty;
    }

    BigDecimal getBuyAmount() {
        return buyAmount;
    }

    void setBuyAmount(BigDecimal buyAmount) {
        this.buyAmount = buyAmount;
    }

    BigDecimal getSellAmount() {
        return sellAmount;
    }

    void setSellAmount(BigDecimal sellAmount) {
        this.sellAmount = sellAmount;
    }

    BigDecimal getNetAmount() {
        return netAmount;
    }

    void setNetAmount(BigDecimal netAmount) {
        this.netAmount = netAmount;
    }
}
