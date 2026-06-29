package np.com.thapanarayan.backend.signal.internal;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.signal.api.StrategyConfigView;
import np.com.thapanarayan.backend.signal.api.StrategyId;

/**
 * Confluence configuration API (§6.4): read the strategy catalog and tune each
 * strategy's weight / enabled flag. The catalog is fixed (the {@link StrategyId}
 * enum), so this is read + update only — there is no creation/deletion.
 *
 * <p>TODO(Stage 8 / IAM): gate updates with role ANALYST/ADMIN once security is wired.</p>
 */
@RestController
@RequestMapping("/api/v1/strategies")
class StrategyController {

    private final StrategyConfigService service;

    StrategyController(StrategyConfigService service) {
        this.service = service;
    }

    @GetMapping
    List<StrategyConfigView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    StrategyConfigView get(@PathVariable StrategyId id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    StrategyConfigView update(@PathVariable StrategyId id, @Valid @RequestBody StrategyConfigUpdate body) {
        return service.update(id, body.enabled(), body.weight());
    }

    record StrategyConfigUpdate(
            @NotNull Boolean enabled,
            @NotNull @PositiveOrZero BigDecimal weight) {
    }
}
