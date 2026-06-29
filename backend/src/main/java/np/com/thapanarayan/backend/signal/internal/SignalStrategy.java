package np.com.thapanarayan.backend.signal.internal;

import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * The strategy contract (§6.1): one interface, every strategy returns a graded
 * vote plus structured reasons. Implementations are Spring beans; the generation
 * service discovers them all and the confluence scorer blends their votes.
 *
 * <p>Ta4j-based strategies (S1–S3, S5–S7) extend {@link Ta4jStrategy}, which derives
 * the vote from a per-bar Ta4j {@code Indicator} so the same logic can be replayed
 * over history for backtesting (Stage 6). Custom strategies (S4, S8) implement this
 * interface directly, reading the volume-profile / broker-flow structures.</p>
 */
interface SignalStrategy {

    StrategyId id();

    /** Evaluate the symbol as-of {@code ctx.tradeDate()} (the series end index). */
    StrategyVote evaluate(SymbolContext ctx);
}
