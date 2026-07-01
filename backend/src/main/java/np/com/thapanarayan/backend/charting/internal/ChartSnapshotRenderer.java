package np.com.thapanarayan.backend.charting.internal;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.signal.api.SignalMarker;

/**
 * Renders a candlestick + volume + signal-marker chart to a PNG using Java2D — headless, dependency
 * free (no browser), and deterministic. Used for the server-side chart snapshots attached to
 * notifications. Pure: it takes data + size and returns image bytes.
 */
final class ChartSnapshotRenderer {

    private static final Color BG = Color.WHITE;
    private static final Color GRID = new Color(0xEC, 0xEF, 0xF3);
    private static final Color TEXT = new Color(0x5B, 0x65, 0x73);
    private static final Color UP = new Color(0x16, 0xA3, 0x4A);
    private static final Color DOWN = new Color(0xDC, 0x26, 0x26);
    private static final Color HOLD = new Color(0x9A, 0xA4, 0xB2);

    private ChartSnapshotRenderer() {
    }

    static byte[] renderPng(String symbol, List<CandleBar> candles, List<SignalMarker> markers,
                            int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, width, height);

            int padL = 52;
            int padR = 12;
            int padT = 26;
            int padB = 16;
            int plotW = width - padL - padR;
            int plotH = height - padT - padB;
            int priceH = (int) (plotH * 0.74);
            int volTop = padT + priceH + 6;
            int volH = plotH - priceH - 6;

            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            String title = candles.isEmpty() ? symbol
                    : symbol + "   " + candles.get(0).tradeDate() + " → " + candles.get(candles.size() - 1).tradeDate();
            g.drawString(title, padL, 17);

            if (candles.isEmpty()) {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
                g.drawString("No data", padL, padT + priceH / 2);
                return encode(img);
            }

            double priceMax = candles.stream().mapToDouble(c -> c.high().doubleValue()).max().orElse(1);
            double priceMin = candles.stream().mapToDouble(c -> c.low().doubleValue()).min().orElse(0);
            double range = Math.max(1e-9, priceMax - priceMin);
            long volMax = Math.max(1, candles.stream().mapToLong(CandleBar::volume).max().orElse(1));

            // price gridlines + labels (max / mid / min)
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            for (int k = 0; k <= 2; k++) {
                double p = priceMax - range * k / 2;
                int y = padT + (int) (priceH * k / 2.0);
                g.setColor(GRID);
                g.drawLine(padL, y, padL + plotW, y);
                g.setColor(TEXT);
                g.drawString(String.format("%.1f", p), 6, y + 4);
            }

            int n = candles.size();
            double slot = (double) plotW / n;
            int bodyW = Math.max(1, (int) (slot * 0.6));
            Map<String, Integer> dateToX = new HashMap<>();

            for (int i = 0; i < n; i++) {
                CandleBar c = candles.get(i);
                int xc = padL + (int) (i * slot + slot / 2);
                dateToX.put(c.tradeDate().toString(), xc);
                double open = c.open().doubleValue();
                double close = c.close().doubleValue();
                Color color = close >= open ? UP : DOWN;

                int yHigh = yFor(c.high().doubleValue(), priceMax, range, padT, priceH);
                int yLow = yFor(c.low().doubleValue(), priceMax, range, padT, priceH);
                int yOpen = yFor(open, priceMax, range, padT, priceH);
                int yClose = yFor(close, priceMax, range, padT, priceH);

                g.setColor(color);
                g.setStroke(new BasicStroke(1f));
                g.drawLine(xc, yHigh, xc, yLow); // wick
                int top = Math.min(yOpen, yClose);
                int h = Math.max(1, Math.abs(yClose - yOpen));
                g.fillRect(xc - bodyW / 2, top, bodyW, h); // body

                // volume
                int vh = (int) ((double) c.volume() / volMax * volH);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 90));
                g.fillRect(xc - bodyW / 2, volTop + volH - vh, bodyW, vh);
            }

            // markers
            for (SignalMarker m : markers) {
                Integer xc = dateToX.get(m.tradeDate());
                if (xc == null) {
                    continue;
                }
                switch (m.action()) {
                    case BUY -> triangle(g, xc, padT + priceH - 6, true, UP);
                    case SELL -> triangle(g, xc, padT + 6, false, DOWN);
                    case HOLD -> {
                        g.setColor(HOLD);
                        g.fillOval(xc - 3, padT + priceH / 2 - 3, 6, 6);
                    }
                }
            }
            return encode(img);
        } finally {
            g.dispose();
        }
    }

    private static int yFor(double price, double priceMax, double range, int padT, int priceH) {
        return padT + (int) ((priceMax - price) / range * priceH);
    }

    private static void triangle(Graphics2D g, int x, int y, boolean up, Color color) {
        g.setColor(color);
        int[] xs = {x - 5, x + 5, x};
        int[] ys = up ? new int[] {y, y, y - 8} : new int[] {y, y, y + 8};
        g.fillPolygon(xs, ys, 3);
    }

    private static byte[] encode(BufferedImage img) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode chart PNG", e);
        }
    }
}
