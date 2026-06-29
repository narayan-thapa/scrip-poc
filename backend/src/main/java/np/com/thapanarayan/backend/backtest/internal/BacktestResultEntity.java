package np.com.thapanarayan.backend.backtest.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import np.com.thapanarayan.backend.backtest.api.BacktestMetricsView;
import np.com.thapanarayan.backend.backtest.api.EquityPointView;

/** The 1:1 result for a run: summary metrics + the equity/drawdown curve as JSONB. */
@Entity
@Table(name = "backtest_result")
class BacktestResultEntity {

    @Id
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false, columnDefinition = "jsonb")
    private BacktestMetricsView metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "equity_curve", nullable = false, columnDefinition = "jsonb")
    private List<EquityPointView> equityCurve;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected BacktestResultEntity() {
    }

    UUID getRunId() {
        return runId;
    }

    void setRunId(UUID runId) {
        this.runId = runId;
    }

    BacktestMetricsView getMetrics() {
        return metrics;
    }

    void setMetrics(BacktestMetricsView metrics) {
        this.metrics = metrics;
    }

    List<EquityPointView> getEquityCurve() {
        return equityCurve;
    }

    void setEquityCurve(List<EquityPointView> equityCurve) {
        this.equityCurve = equityCurve;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
