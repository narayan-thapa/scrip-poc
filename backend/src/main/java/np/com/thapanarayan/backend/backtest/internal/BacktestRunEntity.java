package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import np.com.thapanarayan.backend.backtest.api.BacktestStatus;
import np.com.thapanarayan.backend.backtest.api.CostModelSpec;

/** A backtest run's parameters and lifecycle status (§8). Immutable once created;
 *  a re-run is a new row, so results stay comparable across versions. */
@Entity
@Table(name = "backtest_run")
class BacktestRunEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "strategy_label", length = 128, nullable = false)
    private String strategyLabel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "symbols", nullable = false, columnDefinition = "jsonb")
    private List<String> symbols;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(name = "starting_capital", nullable = false)
    private BigDecimal startingCapital;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cost_model", nullable = false, columnDefinition = "jsonb")
    private CostModelSpec costModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 12, nullable = false)
    private BacktestStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BacktestRunEntity() {
    }

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getStrategyLabel() {
        return strategyLabel;
    }

    void setStrategyLabel(String strategyLabel) {
        this.strategyLabel = strategyLabel;
    }

    List<String> getSymbols() {
        return symbols;
    }

    void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    LocalDate getDateFrom() {
        return dateFrom;
    }

    void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    LocalDate getDateTo() {
        return dateTo;
    }

    void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
    }

    BigDecimal getStartingCapital() {
        return startingCapital;
    }

    void setStartingCapital(BigDecimal startingCapital) {
        this.startingCapital = startingCapital;
    }

    CostModelSpec getCostModel() {
        return costModel;
    }

    void setCostModel(CostModelSpec costModel) {
        this.costModel = costModel;
    }

    BacktestStatus getStatus() {
        return status;
    }

    void setStatus(BacktestStatus status) {
        this.status = status;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
