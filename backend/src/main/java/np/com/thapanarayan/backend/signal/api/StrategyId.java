package np.com.thapanarayan.backend.signal.api;

/**
 * The strategy catalog (§6, table in §5). S1–S3, S5–S7 are Ta4j {@code Indicator}/
 * {@code Rule} strategies; S4 and S8 are custom (volume profile / broker flow). S9
 * is the confluence scorer itself — it blends the others rather than voting, so it
 * is never a weighted input.
 */
public enum StrategyId {

    S1("Trend following"),
    S2("Mean reversion"),
    S3("Momentum breakout"),
    S4("Volume profile"),
    S5("MACD"),
    S6("Supertrend"),
    S7("VWAP / money flow"),
    S8("Broker accumulation / distribution"),
    S9("Confluence");

    private final String label;

    StrategyId(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
