package np.com.thapanarayan.backend.smc.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import np.com.thapanarayan.backend.marketdata.api.DailyCandleView;
import np.com.thapanarayan.backend.smc.api.SmcEventType;
import np.com.thapanarayan.backend.smc.api.SmcView;
import np.com.thapanarayan.backend.smc.api.SmcZoneType;

/** Algorithm-level checks for swing detection, structure breaks, and zones. */
class SmcDetectorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    /** open, high, low, close — volume/turnover/etc. are irrelevant to SMC. */
    private static DailyCandleView candle(int i, double o, double h, double l, double c) {
        BigDecimal open = BigDecimal.valueOf(o);
        return new DailyCandleView("ABC", START.plusDays(i), open, BigDecimal.valueOf(h),
                BigDecimal.valueOf(l), BigDecimal.valueOf(c), 1000L, open, open, null, null, 1);
    }

    @Test
    void detectsBullishBreakOfStructureAndOrderBlock() {
        // Swing high at index 2 (high=15), a down-candle pullback at index 3, then a
        // close at index 5 (16) that breaks above the swing high.
        List<DailyCandleView> candles = List.of(
                candle(0, 9, 10, 8, 9),
                candle(1, 10, 11, 9, 10),
                candle(2, 12, 15, 12, 14),
                candle(3, 14, 13, 10, 11),
                candle(4, 11, 12, 10, 11),
                candle(5, 12, 17, 13, 16));

        SmcView view = SmcDetector.analyze("abc", candles, 2);

        assertThat(view.symbol()).isEqualTo("abc");
        assertThat(view.events()).anySatisfy(e -> {
            assertThat(e.type()).isEqualTo(SmcEventType.BOS_BULLISH);
            assertThat(e.price()).isEqualByComparingTo("15");
            assertThat(e.date()).isEqualTo(START.plusDays(5));
        });
        // The last down candle before the impulse (index 3) is the bullish order block.
        assertThat(view.zones()).anySatisfy(z -> {
            assertThat(z.type()).isEqualTo(SmcZoneType.BULLISH_OB);
            assertThat(z.top()).isEqualByComparingTo("13");
            assertThat(z.bottom()).isEqualByComparingTo("10");
        });
    }

    @Test
    void detectsBullishFairValueGap() {
        // Index 2's low (20) sits entirely above index 0's high (12): a 3-candle gap.
        List<DailyCandleView> candles = List.of(
                candle(0, 10, 12, 9, 11),
                candle(1, 13, 19, 12, 18),
                candle(2, 21, 24, 20, 23),
                candle(3, 23, 25, 22, 24),
                candle(4, 24, 26, 23, 25));

        SmcView view = SmcDetector.analyze("ABC", candles, 2);

        assertThat(view.zones()).anySatisfy(z -> {
            assertThat(z.type()).isEqualTo(SmcZoneType.BULLISH_FVG);
            assertThat(z.bottom()).isEqualByComparingTo("12"); // index 0 high
            assertThat(z.top()).isEqualByComparingTo("20");    // index 2 low
        });
    }

    @Test
    void returnsEmptyWhenTooFewCandles() {
        List<DailyCandleView> candles = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            candles.add(candle(i, 10, 11, 9, 10));
        }

        SmcView view = SmcDetector.analyze("ABC", candles, 2);

        assertThat(view.zones()).isEmpty();
        assertThat(view.events()).isEmpty();
    }
}
