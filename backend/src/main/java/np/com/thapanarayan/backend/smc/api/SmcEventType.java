package np.com.thapanarayan.backend.smc.api;

/**
 * A market-structure break. A <em>BOS</em> (break of structure) continues the
 * prevailing trend; a <em>CHoCH</em> (change of character) is the first break
 * against it, flagging a potential trend reversal.
 */
public enum SmcEventType {
    BOS_BULLISH,
    BOS_BEARISH,
    CHOCH_BULLISH,
    CHOCH_BEARISH
}
