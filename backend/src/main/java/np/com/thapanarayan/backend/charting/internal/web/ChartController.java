package np.com.thapanarayan.backend.charting.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import np.com.thapanarayan.backend.charting.internal.ChartPayload;
import np.com.thapanarayan.backend.charting.internal.ChartPayload.MarkerPayload;
import np.com.thapanarayan.backend.charting.internal.ChartService;
import np.com.thapanarayan.backend.charting.internal.ChartSnapshotService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composite chart payload for Lightweight Charts (F7). One call returns candles + volume + requested
 * indicator overlays + the volume profile + signal markers. ETag-validated so an unchanged chart
 * re-fetches as a cheap 304.
 */
@RestController
@RequestMapping("/api/v1/charts")
@Tag(name = "Charts", description = "Composite chart payload (candles + overlays + profile + markers)")
class ChartController {

    private final ChartService service;
    private final ChartSnapshotService snapshots;

    ChartController(ChartService service, ChartSnapshotService snapshots) {
        this.service = service;
        this.snapshots = snapshots;
    }

    @GetMapping("/{symbol}")
    ResponseEntity<ChartPayload> chart(@PathVariable String symbol,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) List<String> indicators,
                                       @RequestParam(required = false) List<String> overlays,
                                       @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ChartPayload payload = service.chart(symbol, from, to,
                indicators != null ? indicators : List.of(),
                overlays != null ? Set.copyOf(overlays) : Set.of());
        String etag = "\"" + Integer.toHexString(service.signature(payload).hashCode()) + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(payload);
    }

    @GetMapping("/{symbol}/markers")
    List<MarkerPayload> markers(@PathVariable String symbol,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.markers(symbol, from, to);
    }

    /** Server-side PNG snapshot (candles + volume + signal markers) — e.g. embedded in notifications. */
    @GetMapping(value = "/{symbol}/snapshot.png", produces = org.springframework.http.MediaType.IMAGE_PNG_VALUE)
    ResponseEntity<byte[]> snapshot(@PathVariable String symbol,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "820") int width,
                                    @RequestParam(defaultValue = "360") int height) {
        byte[] png = snapshots.snapshotPng(symbol, from, to, bound(width, 320, 1600), bound(height, 160, 900));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofHours(1)))
                .body(png);
    }

    private static int bound(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
