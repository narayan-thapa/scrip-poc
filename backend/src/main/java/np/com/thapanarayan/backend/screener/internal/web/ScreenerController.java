package np.com.thapanarayan.backend.screener.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.ActiveRow;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.DashboardDto;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.PriceDropRow;
import np.com.thapanarayan.backend.screener.internal.ScreenerDtos.RvolRow;
import np.com.thapanarayan.backend.screener.internal.ScreenerProperties;
import np.com.thapanarayan.backend.screener.internal.ScreenerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Day Transaction Analysis dashboard (F11) + screeners (F12): active, relative-volume, price-drop. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Screeners", description = "Day dashboard, activity, relative volume and sharp price-drop")
class ScreenerController {

    private static final Set<String> METRICS = Set.of("pctchange", "drawdown", "sharpness");

    private final ScreenerService service;
    private final ScreenerProperties props;

    ScreenerController(ScreenerService service, ScreenerProperties props) {
        this.service = service;
        this.props = props;
    }

    @GetMapping("/dashboard/day")
    DashboardDto dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(required = false) Integer window) {
        LocalDate d = service.resolveDate(date);
        return service.dayDashboard(d, window != null ? window : props.baselineWindow());
    }

    @GetMapping("/screener/active")
    List<ActiveRow> active(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(defaultValue = "turnover") String by,
                           @RequestParam(defaultValue = "high") String order,
                           @RequestParam(defaultValue = "20") int limit) {
        return service.activeRanking(service.resolveDate(date), by, "high".equalsIgnoreCase(order), bound(limit));
    }

    @GetMapping("/screener/relative-volume")
    List<RvolRow> relativeVolume(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer window,
            @RequestParam(defaultValue = "spike") String type,
            @RequestParam(required = false) Double minRatio,
            @RequestParam(required = false) Double minZScore,
            @RequestParam(defaultValue = "30") int limit) {
        boolean spike = "spike".equalsIgnoreCase(type);
        int w = window != null ? window : props.baselineWindow();
        return service.relativeVolume(service.resolveDate(date), w, spike, minRatio, minZScore, bound(limit));
    }

    @GetMapping("/screener/price-drop")
    List<PriceDropRow> priceDrop(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer window,
            @RequestParam(defaultValue = "pctchange") String metric,
            @RequestParam(required = false) Double threshold,
            @RequestParam(defaultValue = "50") int limit) {
        if (!METRICS.contains(metric)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Unknown metric: " + metric);
        }
        int w = window != null ? window : props.priceDropWindow();
        if (w < 5 || w > 365) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "window must be 5..365");
        }
        LocalDate d = service.resolveDate(date);
        // Presets are pre-warmed/cached; custom windows compute on demand.
        return props.dropPresets().contains(w) && threshold == null
                ? service.priceDropCached(d, w, metric, null, bound(limit))
                : service.priceDrop(d, w, metric, threshold, bound(limit));
    }

    private static int bound(int limit) {
        return Math.max(1, Math.min(200, limit));
    }
}
