package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One round-trip (or still-open) trade in a run's ledger (§8). */
@Entity
@Table(name = "backtest_trade")
class BacktestTradeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "symbol", length = 20, nullable = false)
    private String symbol;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "entry_price", nullable = false)
    private BigDecimal entryPrice;

    @Column(name = "exit_date")
    private LocalDate exitDate;

    @Column(name = "exit_price")
    private BigDecimal exitPrice;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "costs", nullable = false)
    private BigDecimal costs;

    @Column(name = "pnl", nullable = false)
    private BigDecimal pnl;

    @Column(name = "return_pct", nullable = false)
    private BigDecimal returnPct;

    @Column(name = "entry_reason", nullable = false)
    private String entryReason;

    @Column(name = "exit_reason", nullable = false)
    private String exitReason;

    protected BacktestTradeEntity() {
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    UUID getRunId() {
        return runId;
    }

    void setRunId(UUID runId) {
        this.runId = runId;
    }

    String getSymbol() {
        return symbol;
    }

    void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    LocalDate getEntryDate() {
        return entryDate;
    }

    void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    BigDecimal getEntryPrice() {
        return entryPrice;
    }

    void setEntryPrice(BigDecimal entryPrice) {
        this.entryPrice = entryPrice;
    }

    LocalDate getExitDate() {
        return exitDate;
    }

    void setExitDate(LocalDate exitDate) {
        this.exitDate = exitDate;
    }

    BigDecimal getExitPrice() {
        return exitPrice;
    }

    void setExitPrice(BigDecimal exitPrice) {
        this.exitPrice = exitPrice;
    }

    long getQuantity() {
        return quantity;
    }

    void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    BigDecimal getCosts() {
        return costs;
    }

    void setCosts(BigDecimal costs) {
        this.costs = costs;
    }

    BigDecimal getPnl() {
        return pnl;
    }

    void setPnl(BigDecimal pnl) {
        this.pnl = pnl;
    }

    BigDecimal getReturnPct() {
        return returnPct;
    }

    void setReturnPct(BigDecimal returnPct) {
        this.returnPct = returnPct;
    }

    String getEntryReason() {
        return entryReason;
    }

    void setEntryReason(String entryReason) {
        this.entryReason = entryReason;
    }

    String getExitReason() {
        return exitReason;
    }

    void setExitReason(String exitReason) {
        this.exitReason = exitReason;
    }
}
