package np.com.thapanarayan.backend.backtest.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import np.com.thapanarayan.backend.backtest.internal.BacktestDao;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.BacktestRequest;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.EquityPoint;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.RunView;
import np.com.thapanarayan.backend.backtest.internal.BacktestModels.TradeLog;
import np.com.thapanarayan.backend.backtest.internal.BacktestService;
import np.com.thapanarayan.backend.backtest.internal.CostConfig;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Run + inspect + compare backtests. Runs are synchronous (single symbol, EOD data). */
@RestController
@RequestMapping("/api/v1/backtests")
@Tag(name = "Backtests", description = "Confluence-strategy backtests with NEPSE costs")
class BacktestController {

    private final BacktestService service;
    private final BacktestDao dao;

    BacktestController(BacktestService service, BacktestDao dao) {
        this.service = service;
        this.dao = dao;
    }

    record RunRequest(String symbol, String from, String to, Double startingCapital,
                      Double buyThreshold, Double sellThreshold, CostConfig costConfig) {}

    record RunResponse(String runId, Map<String, Double> metrics) {}

    record RunDetail(RunView run, Map<String, Double> metrics) {}

    record CompareRequest(List<String> ids) {}

    @PostMapping
    ResponseEntity<RunResponse> run(@RequestBody RunRequest req, @AuthenticationPrincipal Jwt jwt) {
        BacktestRequest engineReq = new BacktestRequest(
                req.symbol(), LocalDate.parse(req.from()), LocalDate.parse(req.to()),
                req.startingCapital() != null ? req.startingCapital() : 1_000_000,
                req.buyThreshold() != null ? req.buyThreshold() : 35,
                req.sellThreshold() != null ? req.sellThreshold() : 35,
                req.costConfig() != null ? req.costConfig() : CostConfig.defaults());
        UUID id = service.run(engineReq, jwt != null ? jwt.getSubject() : null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RunResponse(id.toString(), dao.metrics(id).orElse(Map.of())));
    }

    @GetMapping
    List<RunView> list(@RequestParam(defaultValue = "50") int limit) {
        return dao.listRuns(limit);
    }

    @GetMapping("/{id}")
    RunDetail detail(@PathVariable UUID id) {
        RunView run = dao.findRun(id).orElseThrow(() -> ApiException.notFound("Unknown run: " + id));
        return new RunDetail(run, dao.metrics(id).orElse(Map.of()));
    }

    @GetMapping("/{id}/metrics")
    Map<String, Double> metrics(@PathVariable UUID id) {
        return dao.metrics(id).orElseThrow(() -> ApiException.notFound("Unknown run: " + id));
    }

    @GetMapping("/{id}/equity-curve")
    List<EquityPoint> equityCurve(@PathVariable UUID id) {
        return dao.equityCurve(id);
    }

    @GetMapping("/{id}/trades")
    List<TradeLog> trades(@PathVariable UUID id) {
        return dao.trades(id);
    }

    @PostMapping("/compare")
    List<RunDetail> compare(@RequestBody CompareRequest req) {
        List<RunDetail> out = new java.util.ArrayList<>();
        for (String raw : req.ids()) {
            UUID id = UUID.fromString(raw);
            dao.findRun(id).ifPresent(run -> out.add(new RunDetail(run, dao.metrics(id).orElse(Map.of()))));
        }
        return out;
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id) {
        return dao.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
