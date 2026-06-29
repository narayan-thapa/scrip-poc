package np.com.thapanarayan.backend.signal.internal;

import java.util.function.IntFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * A custom Ta4j {@link org.ta4j.core.Indicator} whose value at each index is
 * supplied by a function. Strategies build their Ta4j sub-indicators once and wrap
 * a per-bar scoring lambda in one of these, so the <em>same</em> graded-vote logic
 * serves both the as-of-date signal and the historical replay used by the
 * confluence score indicator (and Stage 6 backtests).
 *
 * <p>Extends {@link CachedIndicator} so repeated index access is memoized.</p>
 */
final class FunctionalIndicator extends CachedIndicator<Num> {

    private final IntFunction<Num> fn;
    private final int unstableBars;

    FunctionalIndicator(BarSeries series, int unstableBars, IntFunction<Num> fn) {
        super(series);
        this.unstableBars = unstableBars;
        this.fn = fn;
    }

    @Override
    protected Num calculate(int index) {
        return fn.apply(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }
}
