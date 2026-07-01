package np.com.thapanarayan.backend.indicator.api;

/**
 * How a study renders + is consumed. The charting layer dispatches on this; the signal engine reads
 * numeric kinds as Ta4j indicators and pattern/zone kinds as boolean/event votes.
 */
public enum OutputKind {
    LINE,    // single series (HMA, SMA)
    BAND,    // multiple lines (Bollinger, Keltner)
    SIGNAL,  // a plotted line + discrete buy/sell events (UT Bot, Supertrend)
    MARKER,  // discrete pattern events (Inside Hammer)
    ZONE     // geometric regions: boxes/rays/labels (SMC)
}
