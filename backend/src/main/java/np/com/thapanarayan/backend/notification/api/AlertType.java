package np.com.thapanarayan.backend.notification.api;

/**
 * The kinds of alert rule a user can configure (§10.10). The Stage 9 evaluator reads
 * these on {@code SignalsGeneratedEvent}; the type-specific thresholds live in the
 * rule's {@code params}.
 */
public enum AlertType {
    /** Fire when {@code symbol}'s signal equals {@code params.action}. */
    SIGNAL_ACTION,
    /** Fire when {@code symbol}'s absolute score is at least {@code params.minScore}. */
    SCORE_THRESHOLD,
    /** Fire when any symbol on the user's watchlists gets a non-HOLD signal. */
    WATCHLIST_SIGNAL
}
