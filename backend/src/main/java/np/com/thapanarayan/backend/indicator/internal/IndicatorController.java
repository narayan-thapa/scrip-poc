package np.com.thapanarayan.backend.indicator.internal;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.indicator.api.IndicatorCatalogEntry;
import np.com.thapanarayan.backend.indicator.api.IndicatorSeriesView;
import np.com.thapanarayan.backend.indicator.api.IndicatorSnapshotView;
import np.com.thapanarayan.backend.platform.api.NotFoundException;

/**
 * Read API for indicators plus an admin recompute trigger.
 *
 * <p>TODO(Stage 8 / IAM): gate the compute trigger with role ADMIN once Spring
 * Security is wired; today it is unauthenticated.</p>
 */
@RestController
@RequestMapping("/api/v1/indicators")
@Validated
class IndicatorController {

    private final IndicatorSnapshotService snapshotService;
    private final IndicatorSeriesService seriesService;

    IndicatorController(IndicatorSnapshotService snapshotService, IndicatorSeriesService seriesService) {
        this.snapshotService = snapshotService;
        this.seriesService = seriesService;
    }

    @GetMapping("/catalog")
    List<IndicatorCatalogEntry> catalog() {
        return seriesService.catalog();
    }

    @GetMapping("/{symbol}/latest")
    IndicatorSnapshotView latest(@PathVariable String symbol) {
        return snapshotService.latestSnapshot(symbol)
                .orElseThrow(() -> new NotFoundException("No indicator snapshot for " + symbol));
    }

    @GetMapping("/{symbol}/snapshot")
    IndicatorSnapshotView snapshot(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return snapshotService.snapshot(symbol, date)
                .orElseThrow(() -> new NotFoundException("No indicator snapshot for " + symbol + " on " + date));
    }

    @GetMapping("/{symbol}/series")
    IndicatorSeriesView series(
            @PathVariable String symbol,
            @RequestParam @NotEmpty String indicator,
            @RequestParam(required = false) List<Integer> params,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return seriesService.computeSeries(symbol, indicator, params, from, to);
    }

    @PostMapping("/{symbol}/compute")
    ResponseEntity<IndicatorSnapshotView> compute(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        IndicatorSnapshotView view = snapshotService.computeSnapshot(symbol, date)
                .orElseThrow(() -> new NotFoundException(
                        "No market data to compute indicators for " + symbol + " on " + date));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view);
    }
}
