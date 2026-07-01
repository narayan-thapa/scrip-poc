package np.com.thapanarayan.backend.backtest.internal;

import np.com.thapanarayan.backend.signal.api.ConfluenceModel.ConfluenceFunction;
import np.com.thapanarayan.backend.signal.api.SymbolContext;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Exposes the confluence score as a Ta4j {@code Indicator<Num>} (§6.4) so the whole confluence model
 * can be backtested with {@code Over/UnderIndicatorRule} at the ±thresholds. At each bar it evaluates
 * the strategy panel as-of that bar; the scorer self-guards NaN warm-up values (→ neutral).
 */
class ConfluenceScoreIndicator extends CachedIndicator<Num> {

    private final String symbol;
    private final ConfluenceFunction scorer;

    ConfluenceScoreIndicator(BarSeries series, String symbol, ConfluenceFunction scorer) {
        super(series);
        this.symbol = symbol;
        this.scorer = scorer;
    }

    @Override
    protected Num calculate(int index) {
        double score = scorer.scoreAt(SymbolContext.at(symbol, getBarSeries(), index));
        return getBarSeries().numFactory().numOf(score);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
