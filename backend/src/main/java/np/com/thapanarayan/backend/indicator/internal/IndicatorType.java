package np.com.thapanarayan.backend.indicator.internal;

import java.util.List;
import java.util.Optional;

import np.com.thapanarayan.backend.indicator.api.IndicatorCatalogEntry;
import np.com.thapanarayan.backend.indicator.api.IndicatorCategory;

/**
 * The indicator catalog (§F4): each entry is a stable key, a category, default
 * integer parameters, and the names of the output line(s) it produces. The
 * {@link IndicatorResolver} turns a type + params into live Ta4j indicators.
 */
enum IndicatorType {

    SMA("sma", "Simple Moving Average", IndicatorCategory.TREND, List.of(20), List.of("sma")),
    EMA("ema", "Exponential Moving Average", IndicatorCategory.TREND, List.of(9), List.of("ema")),
    RSI("rsi", "Relative Strength Index", IndicatorCategory.MOMENTUM, List.of(14), List.of("rsi")),
    MACD("macd", "MACD", IndicatorCategory.TREND, List.of(12, 26, 9), List.of("macd", "signal")),
    BBANDS("bbands", "Bollinger Bands", IndicatorCategory.VOLATILITY, List.of(20, 2),
            List.of("upper", "middle", "lower")),
    ATR("atr", "Average True Range", IndicatorCategory.VOLATILITY, List.of(14), List.of("atr")),
    ADX("adx", "Average Directional Index", IndicatorCategory.TREND, List.of(14), List.of("adx")),
    SUPERTREND("supertrend", "Supertrend", IndicatorCategory.TREND, List.of(10, 3), List.of("supertrend")),
    STOCH("stoch", "Stochastic %K", IndicatorCategory.MOMENTUM, List.of(14), List.of("k")),
    CCI("cci", "Commodity Channel Index", IndicatorCategory.MOMENTUM, List.of(20), List.of("cci")),
    ROC("roc", "Rate of Change", IndicatorCategory.MOMENTUM, List.of(12), List.of("roc")),
    WILLR("willr", "Williams %R", IndicatorCategory.MOMENTUM, List.of(14), List.of("willr")),
    OBV("obv", "On-Balance Volume", IndicatorCategory.VOLUME, List.of(), List.of("obv")),
    MFI("mfi", "Money Flow Index", IndicatorCategory.VOLUME, List.of(14), List.of("mfi"));

    private final String key;
    private final String displayName;
    private final IndicatorCategory category;
    private final List<Integer> defaultParams;
    private final List<String> lines;

    IndicatorType(String key, String displayName, IndicatorCategory category,
            List<Integer> defaultParams, List<String> lines) {
        this.key = key;
        this.displayName = displayName;
        this.category = category;
        this.defaultParams = defaultParams;
        this.lines = lines;
    }

    String key() {
        return key;
    }

    List<Integer> defaultParams() {
        return defaultParams;
    }

    List<String> lines() {
        return lines;
    }

    IndicatorCatalogEntry toEntry() {
        return new IndicatorCatalogEntry(key, displayName, category, defaultParams, lines);
    }

    static Optional<IndicatorType> fromKey(String key) {
        for (IndicatorType t : values()) {
            if (t.key.equalsIgnoreCase(key)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
