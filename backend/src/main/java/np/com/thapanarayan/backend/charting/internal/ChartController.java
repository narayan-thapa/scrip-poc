package np.com.thapanarayan.backend.charting.internal;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.charting.api.ChartView;

/**
 * Composite charting endpoint (§9, §10.8):
 * {@code GET /api/v1/charts/{symbol}?from&to&indicators=ema,rsi&overlays=volprofile}.
 *
 * <p>Supports conditional GET: the response carries a content {@code ETag}; a
 * matching {@code If-None-Match} returns {@code 304 Not Modified}. EOD data for a
 * past range is immutable, so a cached chart stays valid until new data lands.</p>
 */
@RestController
@RequestMapping("/api/v1/charts")
class ChartController {

    private final ChartService service;

    ChartController(ChartService service) {
        this.service = service;
    }

    @GetMapping("/{symbol}")
    ResponseEntity<ChartView> chart(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> indicators,
            @RequestParam(required = false) List<String> overlays,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ChartView view = service.compose(symbol, from, to, indicators, overlays);
        String etag = service.etag(view);
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(view);
    }
}
