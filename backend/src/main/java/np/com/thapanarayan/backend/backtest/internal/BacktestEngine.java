package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import np.com.thapanarayan.backend.signal.api.ConfluenceScoreProvider;

/**
 * Replays the confluence model over one symbol's history (§7, §10.7). Ta4j owns
 * trade generation: the entry/exit rules wrap the published confluence-score
 * indicator at ±thresholds, and {@link TradeOnNextOpenModel} fills at the next bar's
 * open — implementing the "signal-at-close, fill-next-open" bias control without any
 * look-ahead. This engine then sizes positions from the capital allocation, applies
 * the {@link NepseCostModel}, and walks a daily sub-account equity curve.
 */
@Component
class BacktestEngine {

    private final ConfluenceScoreProvider scoreProvider;

    BacktestEngine(ConfluenceScoreProvider scoreProvider) {
        this.scoreProvider = scoreProvider;
    }

    /**
     * @param series  warm-up history ending at (or after) {@code to}; only bars in
     *                {@code [from, to]} are traded, earlier bars only warm indicators
     * @param capital capital allocated to this symbol's sub-account
     */
    SymbolRun runSymbol(String symbol, BarSeries series, NepseCostModel costModel,
            BigDecimal capital, LocalDate from, LocalDate to) {
        int fromIdx = firstIndexOnOrAfter(series, from);
        int toIdx = lastIndexOnOrBefore(series, to);
        if (fromIdx < 0 || toIdx < fromIdx) {
            return new SymbolRun(symbol, List.of(), new TreeMap<>(), 0, 0);
        }

        Indicator<Num> score = scoreProvider.scoreIndicator(series);
        double buyT = scoreProvider.buyThreshold();
        double sellT = scoreProvider.sellThreshold();
        Strategy strategy = new BaseStrategy("Confluence",
                new OverIndicatorRule(score, series.numFactory().numOf(buyT)),
                new UnderIndicatorRule(score, series.numFactory().numOf(-sellT)),
                score.getCountOfUnstableBars());

        TradingRecord record = new BarSeriesManager(series, new TradeOnNextOpenModel())
                .run(strategy, Trade.TradeType.BUY, fromIdx, toIdx);

        List<TradeRecord> trades = new ArrayList<>();
        List<PositionSim> sims = new ArrayList<>();
        for (Position position : record.getPositions()) {
            buildTrade(symbol, series, score, position, costModel, capital, toIdx, buyT, sellT)
                    .ifPresent(ts -> {
                        trades.add(ts.trade());
                        sims.add(ts.sim());
                    });
        }
        // A position still open at the period end is marked to the last close.
        Position open = record.getCurrentPosition();
        if (open != null && open.isOpened()) {
            buildOpenTrade(symbol, series, score, open, costModel, capital, toIdx, buyT)
                    .ifPresent(ts -> {
                        trades.add(ts.trade());
                        sims.add(ts.sim());
                    });
        }

        TreeMap<LocalDate, BigDecimal> equity = simulateEquity(series, sims, capital, fromIdx, toIdx);
        int barsInPosition = countBarsInPosition(sims, fromIdx, toIdx);
        return new SymbolRun(symbol, trades, equity, barsInPosition, toIdx - fromIdx + 1);
    }

    private java.util.Optional<TradeAndSim> buildTrade(String symbol, BarSeries series, Indicator<Num> score,
            Position position, NepseCostModel costModel, BigDecimal capital, int toIdx, double buyT, double sellT) {
        int entryIdx = position.getEntry().getIndex();
        BigDecimal rawEntry = position.getEntry().getPricePerAsset().bigDecimalValue();
        boolean closed = position.isClosed();
        int exitIdx = closed ? position.getExit().getIndex() : toIdx;
        BigDecimal rawExit = closed
                ? position.getExit().getPricePerAsset().bigDecimalValue()
                : series.getBar(toIdx).getClosePrice().bigDecimalValue();
        String exitReason = closed
                ? "Confluence score %.1f crossed below -%.0f (exit)".formatted(scoreAt(score, exitIdx), sellT)
                : "Open at period end (marked to last close)";
        return assemble(symbol, series, score, costModel, capital, entryIdx, exitIdx, rawEntry, rawExit, buyT, exitReason);
    }

    private java.util.Optional<TradeAndSim> buildOpenTrade(String symbol, BarSeries series, Indicator<Num> score,
            Position open, NepseCostModel costModel, BigDecimal capital, int toIdx, double buyT) {
        int entryIdx = open.getEntry().getIndex();
        BigDecimal rawEntry = open.getEntry().getPricePerAsset().bigDecimalValue();
        BigDecimal rawExit = series.getBar(toIdx).getClosePrice().bigDecimalValue();
        return assemble(symbol, series, score, costModel, capital, entryIdx, toIdx, rawEntry, rawExit, buyT,
                "Open at period end (marked to last close)");
    }

    private java.util.Optional<TradeAndSim> assemble(String symbol, BarSeries series, Indicator<Num> score,
            NepseCostModel costModel, BigDecimal capital, int entryIdx, int exitIdx,
            BigDecimal rawEntry, BigDecimal rawExit, double buyT, String exitReason) {
        if (rawEntry.signum() <= 0) {
            return java.util.Optional.empty();
        }
        long qty = capital.divide(rawEntry, 0, java.math.RoundingMode.DOWN).longValue();
        if (qty <= 0) {
            return java.util.Optional.empty(); // capital too small to buy a single share
        }
        PositionCosts costs = costModel.roundTrip(rawEntry, rawExit, qty);
        BigDecimal entryValue = costs.entryValue(qty);
        double returnPct = entryValue.signum() > 0
                ? costs.netPnl().divide(entryValue, java.math.MathContext.DECIMAL64).doubleValue() * 100.0
                : 0.0;
        TradeRecord trade = new TradeRecord(symbol,
                dateAt(series, entryIdx), costs.entryFillPrice(),
                dateAt(series, exitIdx), costs.exitFillPrice(), qty,
                costs.totalCosts(), costs.netPnl(), round2(returnPct),
                "Confluence score %.1f crossed above +%.0f (entry)".formatted(scoreAt(score, entryIdx), buyT),
                exitReason);
        PositionSim sim = new PositionSim(entryIdx, exitIdx, qty,
                costs.entryFillPrice(), costs.exitFillPrice(), costs.buyCosts(), costs.sellCosts());
        return java.util.Optional.of(new TradeAndSim(trade, sim));
    }

    /** Walks cash + holdings day by day to produce the sub-account's mark-to-market equity. */
    private TreeMap<LocalDate, BigDecimal> simulateEquity(BarSeries series, List<PositionSim> sims,
            BigDecimal capital, int fromIdx, int toIdx) {
        TreeMap<LocalDate, BigDecimal> curve = new TreeMap<>();
        BigDecimal cash = capital;
        long holdings = 0;
        for (int i = fromIdx; i <= toIdx; i++) {
            for (PositionSim s : sims) {
                if (s.exitIdx() == i) {
                    cash = cash.add(s.exitPrice().multiply(BigDecimal.valueOf(s.quantity())))
                            .subtract(s.sellCosts());
                    holdings -= s.quantity();
                }
            }
            for (PositionSim s : sims) {
                if (s.entryIdx() == i) {
                    cash = cash.subtract(s.entryPrice().multiply(BigDecimal.valueOf(s.quantity())))
                            .subtract(s.buyCosts());
                    holdings += s.quantity();
                }
            }
            BigDecimal close = series.getBar(i).getClosePrice().bigDecimalValue();
            BigDecimal equity = cash.add(close.multiply(BigDecimal.valueOf(holdings)));
            curve.put(dateAt(series, i), equity.setScale(2, java.math.RoundingMode.HALF_UP));
        }
        return curve;
    }

    private static int countBarsInPosition(List<PositionSim> sims, int fromIdx, int toIdx) {
        int count = 0;
        for (int i = fromIdx; i <= toIdx; i++) {
            for (PositionSim s : sims) {
                if (i >= s.entryIdx() && i < s.exitIdx()) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static double scoreAt(Indicator<Num> score, int i) {
        try {
            Num n = score.getValue(i);
            return n == null || n.isNaN() ? 0.0 : n.doubleValue();
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    private static LocalDate dateAt(BarSeries series, int i) {
        return series.getBar(i).getEndTime().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static int firstIndexOnOrAfter(BarSeries series, LocalDate date) {
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            if (!dateAt(series, i).isBefore(date)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOnOrBefore(BarSeries series, LocalDate date) {
        for (int i = series.getEndIndex(); i >= series.getBeginIndex(); i--) {
            if (!dateAt(series, i).isAfter(date)) {
                return i;
            }
        }
        return -1;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record PositionSim(int entryIdx, int exitIdx, long quantity,
            BigDecimal entryPrice, BigDecimal exitPrice, BigDecimal buyCosts, BigDecimal sellCosts) {
    }

    private record TradeAndSim(TradeRecord trade, PositionSim sim) {
    }
}
