package np.com.thapanarayan.backend.charting.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalMarker;
import org.junit.jupiter.api.Test;

class ChartSnapshotRendererTest {

    private static List<CandleBar> series() {
        List<CandleBar> bars = new ArrayList<>();
        LocalDate d = LocalDate.of(2026, 1, 1);
        double p = 100;
        for (int i = 0; i < 40; i++) {
            double o = p;
            double c = p + (i % 2 == 0 ? 1.5 : -1.0);
            bars.add(new CandleBar(d, bd(o), bd(Math.max(o, c) + 1), bd(Math.min(o, c) - 1), bd(c), 1000 + i * 10L));
            p = c;
            d = d.plusDays(1);
        }
        return bars;
    }

    @Test
    void rendersAValidPngOfTheRequestedSize() throws Exception {
        List<SignalMarker> markers = List.of(
                new SignalMarker("s1", "2026-01-05", SignalAction.BUY, 42),
                new SignalMarker("s2", "2026-01-20", SignalAction.SELL, -38));

        byte[] png = ChartSnapshotRenderer.renderPng("NABIL", series(), markers, 820, 360);

        // PNG magic number
        assertThat(png.length).isGreaterThan(1000);
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(new String(png, 1, 3)).isEqualTo("PNG");

        var img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(820);
        assertThat(img.getHeight()).isEqualTo(360);
    }

    @Test
    void rendersPlaceholderForEmptySeries() throws Exception {
        byte[] png = ChartSnapshotRenderer.renderPng("NABIL", List.of(), List.of(), 400, 200);
        var img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(400);
        assertThat(img.getHeight()).isEqualTo(200);
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
