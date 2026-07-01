package np.com.thapanarayan.backend.backtest.internal;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.BacktestOutcome;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.BacktestRequest;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.EquityPoint;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.TradeLog;
import np.com.thapanarayan.backend.indicator.api.BarSeriesFactory;
import np.com.thapanarayan.backend.marketdata.api.CandleBar;
import np.com.thapanarayan.backend.marketdata.api.CandleSeriesReader;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.signal.api.ConfluenceModel;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Runs a confluence-strategy backtest on one symbol: the confluence score is wrapped as a Ta4j
 * indicator and entries/exits fire via Over/Under rules; fills are shifted to the next bar's open
 * ({@link TradeOnNextOpenModel}) to prevent same-bar look-ahead. A custom layer then applies the
 * NEPSE cost model per trade and derives cost-adjusted metrics + equity/drawdown curves + a trade log.
 */
@Service
public class BacktestEngine {

    private final CandleSeriesReader candles;
    private final ConfluenceModel confluenceModel;

    BacktestEngine(CandleSeriesReader candles, ConfluenceModel confluenceModel) {
        this.candles = candles;
        this.confluenceModel = confluenceModel;
    }

    public BacktestOutcome run(BacktestRequest req) {
        List<CandleBar> bars = candles.series(req.symbol(), req.from(), req.to());
        if (bars.size() < 30) {
            throw ApiException.notFound("Not enough history for " + req.symbol() + " in the requested range");
        }
        BarSeries series = BarSeriesFactory.fromCandles(req.symbol(), bars);
        var scoreIndicator = new ConfluenceScoreIndicator(series, req.symbol(), confluenceModel.scorer());

        Strategy strategy = new BaseStrategy(
                new OverIndicatorRule(scoreIndicator, series.numFactory().numOf(req.buyThreshold())),
                new UnderIndicatorRule(scoreIndicator, series.numFactory().numOf(-req.sellThreshold())));

        BarSeriesManager manager = new BarSeriesManager(series, new TradeOnNextOpenModel());
        TradingRecord record = manager.run(strategy);

        NepseCostModel costs = new NepseCostModel(req.costConfig());
        return simulatePortfolio(req, bars, record, costs);
    }

    /** Replay Ta4j's positions with real position sizing + NEPSE costs to get net P&L and curves. */
    private BacktestOutcome simulatePortfolio(BacktestRequest req, List<CandleBar> bars, TradingRecord record,
                                              NepseCostModel costs) {
        double equity = req.startingCapital();
        double peak = equity;
        int barsInMarket = 0;
        List<TradeLog> trades = new ArrayList<>();
        List<EquityPoint> curve = new ArrayList<>();
        curve.add(new EquityPoint(bars.get(0).tradeDate().toString(), equity, 0));

        for (Position position : record.getPositions()) {
            if (!position.isClosed()) {
                continue;
            }
            int entryIdx = position.getEntry().getIndex();
            int exitIdx = position.getExit().getIndex();
            double entryFill = costs.applySlippage(position.getEntry().getPricePerAsset().doubleValue(), true);
            double exitFill = costs.applySlippage(position.getExit().getPricePerAsset().doubleValue(), false);
            long qty = (long) Math.floor(equity / entryFill);
            if (qty <= 0) {
                continue;
            }

            double buyCosts = costs.buyCost(qty * entryFill);
            double sellCosts = costs.sellCost(qty * exitFill);
            double gross = (exitFill - entryFill) * qty;
            double realized = gross - buyCosts - sellCosts;
            double cgt = costs.capitalGainsTax(realized);
            double netPnl = realized - cgt;
            double totalCosts = buyCosts + sellCosts + cgt;
            equity += netPnl;
            peak = Math.max(peak, equity);
            barsInMarket += (exitIdx - entryIdx);

            String exitDate = bars.get(exitIdx).tradeDate().toString();
            trades.add(new TradeLog(bars.get(entryIdx).tradeDate().toString(), round(entryFill), exitDate,
                    round(exitFill), qty, round(totalCosts), round(netPnl),
                    round(netPnl / (entryFill * qty) * 100), "confluence ≥ +" + req.buyThreshold(),
                    "confluence ≤ -" + req.sellThreshold()));
            curve.add(new EquityPoint(exitDate, round(equity), round((equity - peak) / peak * 100)));
        }

        return new BacktestOutcome(metrics(req, bars, equity, trades, barsInMarket), curve, trades);
    }

    private Map<String, Double> metrics(BacktestRequest req, List<CandleBar> bars, double finalEquity,
                                        List<TradeLog> trades, int barsInMarket) {
        double start = req.startingCapital();
        double totalReturnPct = (finalEquity - start) / start * 100;
        long years = Math.max(1, ChronoUnit.DAYS.between(req.from(), req.to())) ;
        double cagr = (Math.pow(finalEquity / start, 365.0 / years) - 1) * 100;

        int wins = 0;
        double grossWin = 0;
        double grossLoss = 0;
        double maxDd = 0;
        double peak = start;
        double eq = start;
        List<Double> returns = new ArrayList<>();
        for (TradeLog t : trades) {
            if (t.pnl() > 0) {
                wins++;
                grossWin += t.pnl();
            } else {
                grossLoss += -t.pnl();
            }
            eq += t.pnl();
            peak = Math.max(peak, eq);
            maxDd = Math.min(maxDd, (eq - peak) / peak * 100);
            returns.add(t.returnPct());
        }

        Map<String, Double> m = new LinkedHashMap<>();
        m.put("numTrades", (double) trades.size());
        m.put("totalReturnPct", round(totalReturnPct));
        m.put("cagrPct", round(cagr));
        m.put("winRatePct", trades.isEmpty() ? 0 : round(100.0 * wins / trades.size()));
        m.put("profitFactor", grossLoss == 0 ? (grossWin > 0 ? 999 : 0) : round(grossWin / grossLoss));
        m.put("maxDrawdownPct", round(maxDd));
        m.put("exposurePct", bars.size() <= 1 ? 0 : round(100.0 * barsInMarket / bars.size()));
        m.put("sortino", round(sortino(returns)));
        m.put("finalEquity", round(finalEquity));
        return m;
    }

    /** Per-trade Sortino: mean return over downside deviation (0 if no downside). */
    private static double sortino(List<Double> returns) {
        if (returns.isEmpty()) {
            return 0;
        }
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double downsideSq = returns.stream().filter(r -> r < 0).mapToDouble(r -> r * r).average().orElse(0);
        double downside = Math.sqrt(downsideSq);
        return downside == 0 ? 0 : mean / downside;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
