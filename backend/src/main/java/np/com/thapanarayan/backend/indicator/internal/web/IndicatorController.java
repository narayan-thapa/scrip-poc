package np.com.thapanarayan.backend.indicator.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.ParamValues;
import np.com.thapanarayan.backend.indicator.internal.ConfigComposedStudies;
import np.com.thapanarayan.backend.indicator.internal.CustomIndicatorRegistry;
import np.com.thapanarayan.backend.indicator.internal.IndicatorService;
import np.com.thapanarayan.backend.indicator.internal.web.IndicatorDtos.ComputeRequest;
import np.com.thapanarayan.backend.indicator.internal.web.IndicatorDtos.ComputeResponse;
import np.com.thapanarayan.backend.indicator.internal.web.IndicatorDtos.CustomStateDto;
import np.com.thapanarayan.backend.indicator.internal.web.IndicatorDtos.DefinitionRequest;
import np.com.thapanarayan.backend.indicator.internal.web.IndicatorDtos.SnapshotDto;
import np.com.thapanarayan.backend.indicator.internal.web.IndicatorDtos.ToggleRequest;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicator catalog + compute + snapshot + custom-study admin (§F4, §6.5). */
@RestController
@RequestMapping("/api/v1/indicators")
@Tag(name = "Indicators", description = "Catalog, compute, snapshots and custom studies")
class IndicatorController {

    private final IndicatorService service;
    private final CustomIndicatorRegistry customs;
    private final ConfigComposedStudies configStudies;

    IndicatorController(IndicatorService service, CustomIndicatorRegistry customs,
                        ConfigComposedStudies configStudies) {
        this.service = service;
        this.customs = customs;
        this.configStudies = configStudies;
    }

    @GetMapping("/catalog")
    List<IndicatorDescriptor> catalog() {
        return service.catalog();
    }

    @GetMapping
    ComputeResponse series(@RequestParam String symbol,
                           @RequestParam String id,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam Map<String, String> allParams) {
        ParamValues params = toParamValues(allParams);
        return ComputeResponse.from(id, service.compute(id, symbol, from, to, params));
    }

    @PostMapping("/compute")
    ComputeResponse compute(@RequestBody ComputeRequest req) {
        ParamValues params = new ParamValues(req.params());
        return ComputeResponse.from(req.id(),
                service.compute(req.id(), req.symbol(), LocalDate.parse(req.from()), LocalDate.parse(req.to()), params));
    }

    @GetMapping("/{symbol}/latest")
    SnapshotDto latest(@PathVariable String symbol) {
        return service.latestSnapshot(symbol).map(SnapshotDto::from)
                .orElseThrow(() -> ApiException.notFound("No indicator snapshot for " + symbol));
    }

    @GetMapping("/custom")
    List<CustomStateDto> customStudies() {
        return customs.all().stream()
                .map(d -> new CustomStateDto(d, customs.isEnabled(d.id())))
                .toList();
    }

    @PatchMapping("/custom/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    void toggle(@PathVariable String id, @RequestBody ToggleRequest req) {
        customs.setEnabled(id, req.enabled());
    }

    @PostMapping("/custom/definitions")
    @PreAuthorize("hasRole('ADMIN')")
    IndicatorDescriptor register(@RequestBody DefinitionRequest req) {
        return configStudies.register(
                new ConfigComposedStudies.Definition(req.id(), req.name(), req.template(), req.fast(), req.slow()));
    }

    /** Coerce free-form query params (everything but symbol/id/from/to) into typed values. */
    private static ParamValues toParamValues(Map<String, String> all) {
        Map<String, Object> params = new HashMap<>();
        all.forEach((k, v) -> {
            if (k.equals("symbol") || k.equals("id") || k.equals("from") || k.equals("to")) {
                return;
            }
            params.put(k, coerce(v));
        });
        return new ParamValues(params);
    }

    private static Object coerce(String v) {
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            return Boolean.parseBoolean(v);
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ignore) {
            // fall through
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException ignore) {
            return v;
        }
    }
}
