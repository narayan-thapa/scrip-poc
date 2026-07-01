package np.com.thapanarayan.backend.signal.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfig;
import np.com.thapanarayan.backend.signal.internal.domain.StrategyConfigRepository;
import np.com.thapanarayan.backend.signal.internal.web.SignalDtos.PatchStrategyRequest;
import np.com.thapanarayan.backend.signal.internal.web.SignalDtos.StrategyConfigDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Strategy configs: list/inspect, and (analyst) tune weight/enabled — drives confluence blending. */
@RestController
@RequestMapping("/api/v1/strategies")
@Tag(name = "Strategies", description = "Strategy weights and enablement")
class StrategyController {

    private final StrategyConfigRepository repo;

    StrategyController(StrategyConfigRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    List<StrategyConfigDto> list() {
        return repo.findAll().stream().map(StrategyConfigDto::from).toList();
    }

    @GetMapping("/{id}")
    StrategyConfigDto get(@PathVariable String id) {
        return repo.findById(id).map(StrategyConfigDto::from)
                .orElseThrow(() -> ApiException.notFound("Unknown strategy: " + id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'ADMIN')")
    @Transactional
    StrategyConfigDto patch(@PathVariable String id, @RequestBody PatchStrategyRequest req) {
        StrategyConfig config = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Unknown strategy: " + id));
        if (req.weight() != null) {
            config.setWeight(BigDecimal.valueOf(req.weight()));
        }
        if (req.enabled() != null) {
            config.setEnabled(req.enabled());
        }
        return StrategyConfigDto.from(repo.save(config));
    }
}
