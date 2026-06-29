package np.com.thapanarayan.backend.indicator.internal;

import java.time.LocalDate;
import java.util.List;

import org.ta4j.core.BarSeries;

/**
 * A Ta4j {@link BarSeries} paired with the trade date of each bar, so indicator
 * values (addressed by bar index) can be mapped back to dates. Ta4j's own bar
 * end-time is an {@code Instant}; keeping the {@code LocalDate} list alongside
 * avoids a zone round-trip when emitting date-keyed series.
 *
 * @param series Ta4j series, bars ascending by date
 * @param dates  trade date per bar, same order/size as the series bars
 */
record IndicatorBarSeries(BarSeries series, List<LocalDate> dates) {

    int endIndex() {
        return series.getEndIndex();
    }

    int barCount() {
        return series.getBarCount();
    }
}
