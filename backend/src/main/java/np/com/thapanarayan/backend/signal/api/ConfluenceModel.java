package np.com.thapanarayan.backend.signal.api;

/**
 * Published access to the confluence score so the backtester can wrap it as a Ta4j indicator (§6.4).
 * {@link #scorer()} snapshots the currently enabled strategies + weights into a reusable function,
 * so a per-bar backtest sweep doesn't re-read config on every index.
 */
public interface ConfluenceModel {

    ConfluenceFunction scorer();

    /** Computes the confluence score ∈ [-100, +100] for a symbol at a given bar. */
    @FunctionalInterface
    interface ConfluenceFunction {
        double scoreAt(SymbolContext ctx);
    }
}
