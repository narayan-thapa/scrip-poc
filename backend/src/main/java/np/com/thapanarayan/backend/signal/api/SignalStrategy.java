package np.com.thapanarayan.backend.signal.api;

/**
 * One independent strategy. Implementations express their conditions (often as Ta4j rules over Ta4j
 * indicators), evaluate at the latest bar, and map the outcome + underlying values into a graded
 * {@link StrategyVote} with structured reasons. Registered as Spring beans and blended by the
 * confluence scorer; the default weight is config-overridable.
 */
public interface SignalStrategy {

    /** Stable id, e.g. {@code "S1"} / {@code "trend-following"}. */
    String id();

    String name();

    /** Default confluence weight (overridable via strategy_config). */
    default double defaultWeight() {
        return 1.0;
    }

    StrategyVote evaluate(SymbolContext ctx);
}
