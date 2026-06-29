package np.com.thapanarayan.backend.backtest.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.backtest.api.BacktestMetricsView;
import np.com.thapanarayan.backend.backtest.api.BacktestRunView;
import np.com.thapanarayan.backend.backtest.api.BacktestTradeView;
import np.com.thapanarayan.backend.backtest.api.EquityPointView;
import np.com.thapanarayan.backend.platform.api.PageResponse;

/**
 * Backtesting API (§9). Runs execute synchronously and return the completed run
 * (or a FAILED run carrying the error). Equity curve and trade ledger have their own
 * endpoints to keep the run envelope light.
 *
 * <p>TODO(Stage 8 / IAM): gate run/delete with role ANALYST/ADMIN once security is wired.</p>
 */
@RestController
@RequestMapping("/api/v1/backtests")
@Validated
class BacktestController {

    private final BacktestService service;

    BacktestController(BacktestService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<BacktestRunView> run(@Valid @RequestBody BacktestRequest request) {
        BacktestRunView view = service.run(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @GetMapping
    PageResponse<BacktestRunView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/{id}")
    BacktestRunView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/metrics")
    BacktestMetricsView metrics(@PathVariable UUID id) {
        return service.metrics(id);
    }

    @GetMapping("/{id}/equity-curve")
    List<EquityPointView> equityCurve(@PathVariable UUID id) {
        return service.equityCurve(id);
    }

    @GetMapping("/{id}/trades")
    List<BacktestTradeView> trades(@PathVariable UUID id) {
        return service.trades(id);
    }

    @PostMapping("/compare")
    List<BacktestRunView> compare(@Valid @RequestBody CompareRequest request) {
        return service.compare(request.runIds());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    record CompareRequest(@NotEmpty List<UUID> runIds) {
    }
}
