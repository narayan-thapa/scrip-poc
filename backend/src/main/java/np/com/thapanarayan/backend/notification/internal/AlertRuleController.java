package np.com.thapanarayan.backend.notification.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.iam.api.CurrentUserProvider;
import np.com.thapanarayan.backend.notification.api.AlertRuleView;
import np.com.thapanarayan.backend.notification.api.AlertType;

/** Alert-rule CRUD (§9), scoped to the authenticated user. */
@RestController
@RequestMapping("/api/v1/alerts")
class AlertRuleController {

    private final AlertRuleService service;
    private final CurrentUserProvider currentUser;

    AlertRuleController(AlertRuleService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<AlertRuleView> list() {
        return service.list(currentUser.currentUserId());
    }

    @GetMapping("/{id}")
    AlertRuleView get(@PathVariable UUID id) {
        return service.get(currentUser.currentUserId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AlertRuleView create(@Valid @RequestBody CreateRequest body) {
        return service.create(currentUser.currentUserId(), body.type(), body.symbol(), body.params());
    }

    @PutMapping("/{id}")
    AlertRuleView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body) {
        return service.update(currentUser.currentUserId(), id, body.enabled(), body.symbol(), body.params());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(currentUser.currentUserId(), id);
    }

    record CreateRequest(@NotNull AlertType type, String symbol, Map<String, Object> params) {
    }

    record UpdateRequest(boolean enabled, String symbol, Map<String, Object> params) {
    }
}
