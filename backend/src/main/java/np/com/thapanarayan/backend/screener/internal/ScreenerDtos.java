package np.com.thapanarayan.backend.screener.internal;

import java.math.BigDecimal;
import java.util.List;

/** Response payloads for the screener + dashboard API. */
public final class ScreenerDtos {

    private ScreenerDtos() {
    }

    /** Common annotation carried on every row: the scrip's current signal. */
    public record SignalTag(String action, Double score) {}

    public record ActiveRow(String symbol, BigDecimal close, BigDecimal changePct, long volume,
                            BigDecimal turnover, int tradesCount, SignalTag signal) {}

    public record RvolRow(String symbol, long volume, double rvolRatio, double rvolZ, BigDecimal changePct,
                          SignalTag signal) {}

    public record PriceDropRow(String symbol, BigDecimal close, BigDecimal closeNAgo, double pctChange,
                               double drawdownFromHigh, double sharpness, BigDecimal windowHigh, BigDecimal windowLow,
                               long volume, double rvolRatio, boolean insufficientHistory, SignalTag signal) {}

    public record BreadthDto(int advances, int declines, int unchanged, long totalVolume,
                             BigDecimal totalTurnover, long totalTrades) {}

    public record DashboardDto(String date, BreadthDto breadth, List<ActiveRow> highTrade, List<ActiveRow> lowTrade,
                               List<RvolRow> rvolSpikes, List<RvolRow> rvolDrops) {}
}
