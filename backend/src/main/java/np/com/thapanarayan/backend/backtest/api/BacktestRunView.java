package np.com.thapanarayan.backend.backtest.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A backtest run: its parameters and (once complete) its summary metrics. The
 * equity curve and trade ledger are fetched via their own endpoints to keep this
 * envelope light.
 *
 * @param strategyLabel human-readable strategy description (the confluence model)
 * @param metrics       summary metrics, or {@code null} until the run completes
 * @param errorMessage  failure detail when {@code status == FAILED}, else {@code null}
 */
public record BacktestRunView(
        UUID id,
        String strategyLabel,
        List<String> symbols,
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal startingCapital,
        CostModelSpec costModel,
        BacktestStatus status,
        BacktestMetricsView metrics,
        String errorMessage,
        Instant createdAt) {
}
