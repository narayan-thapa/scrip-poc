package np.com.thapanarayan.backend.notification.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import np.com.thapanarayan.backend.notification.internal.domain.AlertRule;
import np.com.thapanarayan.backend.notification.internal.domain.AlertRuleRepository;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User alert rules (F9). Types: {@code SIGNAL_ACTION} (params: symbol, action) is evaluated on each
 * signal run; {@code RVOL_SPIKE}/{@code RVOL_DROP}/{@code PRICE_DROP} are accepted for forward-compat
 * (screener-driven evaluation is a follow-on). Watchlist symbols notify implicitly on BUY/SELL.
 */
@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "User alert rules")
class AlertController {

    private final AlertRuleRepository repo;

    AlertController(AlertRuleRepository repo) {
        this.repo = repo;
    }

    record AlertRuleDto(String id, String type, Map<String, Object> params, boolean enabled) {
        static AlertRuleDto from(AlertRule r) {
            return new AlertRuleDto(r.getId().toString(), r.getType(), r.getParams(), r.isEnabled());
        }
    }

    record CreateRequest(@NotBlank String type, Map<String, Object> params) {}

    record PatchRequest(Boolean enabled, Map<String, Object> params) {}

    @GetMapping
    List<AlertRuleDto> list(@AuthenticationPrincipal Jwt jwt) {
        return repo.findByUserId(userId(jwt)).stream().map(AlertRuleDto::from).toList();
    }

    @PostMapping
    AlertRuleDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateRequest req) {
        return AlertRuleDto.from(repo.save(new AlertRule(userId(jwt), req.type(), req.params())));
    }

    @PatchMapping("/{id}")
    @Transactional
    AlertRuleDto patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody PatchRequest req) {
        AlertRule rule = owned(jwt, id);
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }
        if (req.params() != null) {
            rule.setParams(req.params());
        }
        return AlertRuleDto.from(repo.save(rule));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        repo.delete(owned(jwt, id));
        return ResponseEntity.noContent().build();
    }

    private AlertRule owned(Jwt jwt, UUID id) {
        AlertRule rule = repo.findById(id).orElseThrow(() -> ApiException.notFound("Unknown alert: " + id));
        if (!rule.getUserId().equals(userId(jwt))) {
            throw ApiException.notFound("Unknown alert: " + id);
        }
        return rule;
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
