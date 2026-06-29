package np.com.thapanarayan.backend.backtest.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ta4j.core.BarSeries;

import np.com.thapanarayan.backend.backtest.api.BacktestMetricsView;
import np.com.thapanarayan.backend.backtest.api.BacktestRunView;
import np.com.thapanarayan.backend.backtest.api.BacktestStatus;
import np.com.thapanarayan.backend.backtest.api.BacktestTradeView;
import np.com.thapanarayan.backend.backtest.api.BacktestUpdatedEvent;
import np.com.thapanarayan.backend.backtest.api.CostModelSpec;
import np.com.thapanarayan.backend.backtest.api.EquityPointView;
import np.com.thapanarayan.backend.indicator.api.BarSeriesProvider;
import np.com.thapanarayan.backend.platform.api.DomainEventPublisher;
import np.com.thapanarayan.backend.platform.api.DomainException;
import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.platform.api.PageResponse;

/**
 * Orchestrates backtest runs (§10.7): builds each symbol's warm-up series, drives
 * the {@link BacktestEngine}, aggregates the per-symbol results into a shared-capital
 * portfolio (equal allocation), computes cost-adjusted metrics, persists the run /
 * result / trade ledger, and serves the read endpoints. Runs execute synchronously.
 */
@Service
class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);
    private static final String STRATEGY_LABEL = "Confluence (S9)";

    private final BacktestEngine engine;
    private final BarSeriesProvider barSeries;
    private final BacktestRunRepository runs;
    private final BacktestResultRepository results;
    private final BacktestTradeRepository trades;
    private final BacktestProperties properties;
    private final DomainEventPublisher events;
    private final NepseClock clock;

    BacktestService(BacktestEngine engine, BarSeriesProvider barSeries, BacktestRunRepository runs,
            BacktestResultRepository results, BacktestTradeRepository trades, BacktestProperties properties,
            DomainEventPublisher events, NepseClock clock) {
        this.engine = engine;
        this.barSeries = barSeries;
        this.runs = runs;
        this.results = results;
        this.trades = trades;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public BacktestRunView run(BacktestRequest request) {
        List<String> symbols = normalize(request.symbols());
        validate(symbols, request.from(), request.to());
        BigDecimal startingCapital = request.startingCapital() != null
                ? request.startingCapital() : properties.startingCapital();
        CostModelSpec costSpec = request.costModel() != null
                ? request.costModel() : properties.defaultCostModel();

        BacktestRunEntity run = new BacktestRunEntity();
        run.setId(UUID.randomUUID());
        run.setStrategyLabel(STRATEGY_LABEL);
        run.setSymbols(symbols);
        run.setDateFrom(request.from());
        run.setDateTo(request.to());
        run.setStartingCapital(startingCapital);
        run.setCostModel(costSpec);
        run.setStatus(BacktestStatus.RUNNING);
        run.setCreatedAt(Instant.now(clock.clock()));
        runs.save(run);

        try {
            execute(run, symbols, request.from(), request.to(), startingCapital, costSpec);
            run.setStatus(BacktestStatus.COMPLETED);
            runs.save(run);
            events.publish(new BacktestUpdatedEvent(run.getId(), request.to(), symbols));
        } catch (RuntimeException e) {
            log.error("Backtest {} failed", run.getId(), e);
            run.setStatus(BacktestStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            runs.save(run);
        }
        return BacktestMapper.runView(run, results.findById(run.getId()).orElse(null));
    }

    private void execute(BacktestRunEntity run, List<String> symbols, LocalDate from, LocalDate to,
            BigDecimal startingCapital, CostModelSpec costSpec) {
        NepseCostModel costModel = new NepseCostModel(costSpec);
        BigDecimal capitalPerSymbol = startingCapital.divide(
                BigDecimal.valueOf(symbols.size()), 2, RoundingMode.DOWN);
        int lookback = properties.warmupBars() + (int) ChronoUnit.DAYS.between(from, to) + 1;

        List<SymbolRun> symbolRuns = new ArrayList<>();
        for (String symbol : symbols) {
            BarSeries series = barSeries.dailySeries(symbol, to, lookback);
            symbolRuns.add(engine.runSymbol(symbol, series, costModel, capitalPerSymbol, from, to));
        }

        List<EquityPoint> portfolio = aggregateEquity(symbolRuns, capitalPerSymbol);
        List<TradeStat> stats = symbolRuns.stream()
                .flatMap(r -> r.trades().stream())
                .map(t -> new TradeStat(t.returnPct(), t.pnl(), t.costs()))
                .toList();
        int barsInPosition = symbolRuns.stream().mapToInt(SymbolRun::barsInPosition).sum();
        int totalBars = symbolRuns.stream().mapToInt(SymbolRun::totalBars).sum();

        BacktestMetricsView metrics = MetricsCalculator.compute(
                startingCapital, portfolio, stats, barsInPosition, totalBars);

        persistResult(run.getId(), metrics, withDrawdown(portfolio));
        persistTrades(run.getId(), symbolRuns);
    }

    /** Sum each symbol's sub-account equity over the union of trading days (carry-forward, default = idle capital). */
    private static List<EquityPoint> aggregateEquity(List<SymbolRun> symbolRuns, BigDecimal capitalPerSymbol) {
        TreeSet<LocalDate> dates = new TreeSet<>();
        symbolRuns.forEach(r -> dates.addAll(r.equityByDate().keySet()));
        List<EquityPoint> portfolio = new ArrayList<>(dates.size());
        for (LocalDate date : dates) {
            BigDecimal total = BigDecimal.ZERO;
            for (SymbolRun r : symbolRuns) {
                var entry = r.equityByDate().floorEntry(date);
                total = total.add(entry != null ? entry.getValue() : capitalPerSymbol);
            }
            portfolio.add(new EquityPoint(date, total));
        }
        return portfolio;
    }

    /** Annotates the curve with running peak-to-trough drawdown for the chart. */
    private static List<EquityPointView> withDrawdown(List<EquityPoint> portfolio) {
        List<EquityPointView> out = new ArrayList<>(portfolio.size());
        double peak = Double.NEGATIVE_INFINITY;
        for (EquityPoint p : portfolio) {
            double eq = p.equity().doubleValue();
            peak = Math.max(peak, eq);
            double ddPct = peak > 0 ? (eq - peak) / peak * 100.0 : 0.0;
            out.add(new EquityPointView(p.date(), p.equity(), Math.round(ddPct * 100.0) / 100.0));
        }
        return out;
    }

    private void persistResult(UUID runId, BacktestMetricsView metrics, List<EquityPointView> curve) {
        BacktestResultEntity result = new BacktestResultEntity();
        result.setRunId(runId);
        result.setMetrics(metrics);
        result.setEquityCurve(curve);
        result.setCreatedAt(Instant.now(clock.clock()));
        results.save(result);
    }

    private void persistTrades(UUID runId, List<SymbolRun> symbolRuns) {
        List<BacktestTradeEntity> entities = new ArrayList<>();
        for (SymbolRun r : symbolRuns) {
            for (TradeRecord t : r.trades()) {
                BacktestTradeEntity e = new BacktestTradeEntity();
                e.setId(UUID.randomUUID());
                e.setRunId(runId);
                e.setSymbol(t.symbol());
                e.setEntryDate(t.entryDate());
                e.setEntryPrice(t.entryPrice());
                e.setExitDate(t.exitDate());
                e.setExitPrice(t.exitPrice());
                e.setQuantity(t.quantity());
                e.setCosts(t.costs());
                e.setPnl(t.pnl());
                e.setReturnPct(BigDecimal.valueOf(t.returnPct()));
                e.setEntryReason(t.entryReason());
                e.setExitReason(t.exitReason());
                entities.add(e);
            }
        }
        trades.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public BacktestRunView get(UUID id) {
        BacktestRunEntity run = runs.findById(id)
                .orElseThrow(() -> new NotFoundException("No backtest run " + id));
        return BacktestMapper.runView(run, results.findById(id).orElse(null));
    }

    @Transactional(readOnly = true)
    public PageResponse<BacktestRunView> list(int page, int size) {
        var pageResult = runs.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(run -> BacktestMapper.runView(run, results.findById(run.getId()).orElse(null)));
        return PageResponse.from(pageResult);
    }

    @Transactional(readOnly = true)
    public BacktestMetricsView metrics(UUID id) {
        requireRun(id);
        return results.findById(id)
                .map(BacktestResultEntity::getMetrics)
                .orElseThrow(() -> new NotFoundException("No metrics for backtest " + id + " (run incomplete?)"));
    }

    @Transactional(readOnly = true)
    public List<EquityPointView> equityCurve(UUID id) {
        requireRun(id);
        return results.findById(id)
                .map(BacktestResultEntity::getEquityCurve)
                .orElseThrow(() -> new NotFoundException("No equity curve for backtest " + id + " (run incomplete?)"));
    }

    @Transactional(readOnly = true)
    public List<BacktestTradeView> trades(UUID id) {
        requireRun(id);
        return trades.findByRunIdOrderByEntryDateAsc(id).stream().map(BacktestMapper::tradeView).toList();
    }

    @Transactional(readOnly = true)
    public List<BacktestRunView> compare(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new DomainException("INVALID_COMPARE", "Provide at least one run id to compare");
        }
        return ids.stream().map(this::get).toList();
    }

    @Transactional
    public void delete(UUID id) {
        requireRun(id);
        runs.deleteById(id); // result + trades cascade via FK ON DELETE CASCADE
    }

    private void requireRun(UUID id) {
        if (!runs.existsById(id)) {
            throw new NotFoundException("No backtest run " + id);
        }
    }

    private static List<String> normalize(List<String> symbols) {
        return symbols == null ? List.of()
                : symbols.stream().filter(s -> s != null && !s.isBlank())
                        .map(s -> s.trim().toUpperCase()).distinct().toList();
    }

    private static void validate(List<String> symbols, LocalDate from, LocalDate to) {
        if (symbols.isEmpty()) {
            throw new DomainException("NO_SYMBOLS", "At least one symbol is required");
        }
        if (from == null || to == null || from.isAfter(to)) {
            throw new DomainException("INVALID_RANGE", "from must be on or before to");
        }
    }
}
