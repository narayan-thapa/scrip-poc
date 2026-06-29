package np.com.thapanarayan.backend.signal.api;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Published access to the confluence score as a Ta4j {@link Indicator} (§6.4
 * backtesting hook). The backtest engine (Stage 6) builds its entry/exit rules from
 * this — {@code OverIndicatorRule(scoreIndicator, +buyThreshold)} /
 * {@code UnderIndicatorRule(scoreIndicator, -sellThreshold)} — so the very same
 * confluence model that produces daily signals is what gets backtested, without the
 * backtest module reaching into the signal {@code internal} package.
 */
public interface ConfluenceScoreProvider {

    /**
     * The confluence score ∈ [-100, +100] at each bar of {@code series}, blended
     * from the enabled Ta4j strategies' graded votes at their configured weights.
     */
    Indicator<Num> scoreIndicator(BarSeries series);

    /** Score at or above which the action is BUY (the {@code +T_buy} entry threshold). */
    double buyThreshold();

    /** Score at or below {@code -sellThreshold} which the action is SELL (the exit threshold). */
    double sellThreshold();
}
