package np.com.thapanarayan.backend.backtest.api;

/** Lifecycle of a backtest run. Runs execute synchronously today, but the status is
 *  persisted so the API and a future async/queued executor share one contract. */
public enum BacktestStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
