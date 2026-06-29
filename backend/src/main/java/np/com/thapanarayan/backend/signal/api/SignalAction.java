package np.com.thapanarayan.backend.signal.api;

/**
 * The actionable outcome of the confluence scorer (§6.4). Derived from the score
 * against the configured {@code +T_buy} / {@code -T_sell} thresholds.
 */
public enum SignalAction {
    BUY,
    SELL,
    HOLD
}
