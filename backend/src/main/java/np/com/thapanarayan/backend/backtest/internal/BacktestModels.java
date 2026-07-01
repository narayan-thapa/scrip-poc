package np.com.thapanarayan.backend.backtest.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Engine I/O records for a backtest run. */
public final class BacktestModels {

    private BacktestModels() {
    }

    public record BacktestRequest(String symbol, LocalDate from, LocalDate to, double startingCapital,
                                  double buyThreshold, double sellThreshold, CostConfig costConfig) {}

    public record EquityPoint(String date, double equity, double drawdownPct) {}

    public record TradeLog(String entryDate, double entryPrice, String exitDate, double exitPrice, long quantity,
                           double costs, double pnl, double returnPct, String entryReason, String exitReason) {}

    public record BacktestOutcome(Map<String, Double> metrics, List<EquityPoint> equityCurve, List<TradeLog> trades) {}

    /** Persisted run header (for list/detail reads). */
    public record RunView(String id, String symbol, String from, String to, double startingCapital,
                          double buyThreshold, double sellThreshold, String status, String createdAt) {}
}
