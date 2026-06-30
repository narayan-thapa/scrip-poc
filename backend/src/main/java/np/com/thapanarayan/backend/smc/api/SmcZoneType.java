package np.com.thapanarayan.backend.smc.api;

/**
 * Kind of price/time region produced by SMC analysis.
 *
 * <ul>
 *   <li>{@code BULLISH_OB}/{@code BEARISH_OB} — order blocks: the last opposite
 *       candle before an impulsive move that broke structure.</li>
 *   <li>{@code BULLISH_FVG}/{@code BEARISH_FVG} — fair-value gaps: a three-candle
 *       price imbalance.</li>
 * </ul>
 */
public enum SmcZoneType {
    BULLISH_OB,
    BEARISH_OB,
    BULLISH_FVG,
    BEARISH_FVG
}
