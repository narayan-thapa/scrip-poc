package np.com.thapanarayan.backend.signal.api;

/**
 * A single structured, first-class reason behind a strategy's vote (§6.1). Rendered
 * to text for the UI <em>and</em> available as data, so every signal is auditable.
 *
 * @param strategyId    the strategy that produced this reason
 * @param indicator     the indicator(s) involved, e.g. {@code "EMA(9/21)+ADX"}
 * @param condition     the boolean trigger that fired, e.g. {@code "EMA9 crossed above EMA21"}
 * @param observedValue the observed indicator values, formatted for display
 * @param threshold     the threshold/condition the observation was compared against
 * @param contribution  signed contribution to the score, {@code vote * confidence} ∈ [-1, +1]
 * @param narrative     a human-readable sentence explaining the reason
 */
public record ReasonView(
        StrategyId strategyId,
        String indicator,
        String condition,
        String observedValue,
        String threshold,
        double contribution,
        String narrative) {
}
