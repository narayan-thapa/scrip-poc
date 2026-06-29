package np.com.thapanarayan.backend.backtest.internal;

import np.com.thapanarayan.backend.backtest.api.BacktestRunView;
import np.com.thapanarayan.backend.backtest.api.BacktestTradeView;

/** Entity → published-view conversion for backtests. */
final class BacktestMapper {

    private BacktestMapper() {
    }

    static BacktestRunView runView(BacktestRunEntity run, BacktestResultEntity result) {
        return new BacktestRunView(
                run.getId(), run.getStrategyLabel(), run.getSymbols(),
                run.getDateFrom(), run.getDateTo(), run.getStartingCapital(), run.getCostModel(),
                run.getStatus(), result == null ? null : result.getMetrics(),
                run.getErrorMessage(), run.getCreatedAt());
    }

    static BacktestTradeView tradeView(BacktestTradeEntity t) {
        return new BacktestTradeView(
                t.getSymbol(), t.getEntryDate(), t.getEntryPrice(), t.getExitDate(), t.getExitPrice(),
                t.getQuantity(), t.getCosts(), t.getPnl(),
                t.getReturnPct() == null ? 0.0 : t.getReturnPct().doubleValue(),
                t.getEntryReason(), t.getExitReason());
    }
}
