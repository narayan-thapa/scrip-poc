package np.com.thapanarayan.backend.screener.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

class ScreenerCalcTest {

    private static final Offset<Double> EPS = Offset.offset(0.01);

    @Test
    void rvolRatioAndZScore() {
        // baseline mean = 100, sd = 0 → ratio for 300 today = 3.0, z = 0 (no dispersion)
        var flat = ScreenerCalc.rvol(List.of(100L, 100L, 100L, 100L), 300);
        assertThat(flat.ratio()).isCloseTo(3.0, EPS);
        assertThat(flat.zScore()).isZero();

        // baseline {100,200,100,200} mean=150, sd=50 → today 300: ratio=2.0, z=(300-150)/50=3.0
        var spread = ScreenerCalc.rvol(List.of(100L, 200L, 100L, 200L), 300);
        assertThat(spread.ratio()).isCloseTo(2.0, EPS);
        assertThat(spread.zScore()).isCloseTo(3.0, EPS);
    }

    @Test
    void priceDropLenses() {
        // close 80 vs 100 N ago → -20%; window high 120 → drawdown (80-120)/120 = -33.33%
        // sharpness = -20 / (2.5% * sqrt(30)) = -20 / (2.5*5.477) = -1.46
        var d = ScreenerCalc.priceDrop(80, 100, 120, 2.5, 30);
        assertThat(d.pctChange()).isCloseTo(-20.0, EPS);
        assertThat(d.drawdownFromHigh()).isCloseTo(-33.33, EPS);
        assertThat(d.sharpness()).isCloseTo(-1.46, EPS);
    }
}
