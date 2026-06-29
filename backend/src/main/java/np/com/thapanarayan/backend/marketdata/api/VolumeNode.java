package np.com.thapanarayan.backend.marketdata.api;

/**
 * Classification of a volume-profile price bin (§6.2).
 *
 * <ul>
 *   <li>{@link #HVN} — High Volume Node: a volume peak (support/resistance shelf).</li>
 *   <li>{@link #LVN} — Low Volume Node: a volume valley (thin liquidity; fast moves).</li>
 *   <li>{@link #NEUTRAL} — neither.</li>
 * </ul>
 */
public enum VolumeNode {
    NEUTRAL,
    HVN,
    LVN
}
