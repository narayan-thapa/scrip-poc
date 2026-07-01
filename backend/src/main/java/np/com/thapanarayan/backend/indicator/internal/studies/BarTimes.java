package np.com.thapanarayan.backend.indicator.internal.studies;

import java.time.ZoneOffset;
import org.ta4j.core.BarSeries;

/** Maps a Ta4j bar index to its business-day string (YYYY-MM-DD) for chart-aligned results. */
final class BarTimes {

    private BarTimes() {
    }

    static String at(BarSeries series, int index) {
        return series.getBar(index).getEndTime().atOffset(ZoneOffset.UTC).toLocalDate().toString();
    }
}
