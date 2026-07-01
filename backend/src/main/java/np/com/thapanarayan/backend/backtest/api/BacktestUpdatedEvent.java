package np.com.thapanarayan.backend.backtest.api;

/** Published after a backtest run completes (id + symbol), for any downstream refresh/caching. */
public record BacktestUpdatedEvent(String runId, String symbol) {
}
