package np.com.thapanarayan.backend.platform.internal.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.OffsetDateTime;
import np.com.thapanarayan.backend.platform.api.time.NepalClock;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal system endpoint used as the Phase 0 contract-first handshake: it round-trips through the
 * OpenAPI spec into the frontend's generated DTOs, proving the end-to-end wiring before any feature
 * exists. Liveness/readiness proper live under {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Liveness and build/version introspection")
class SystemController {

    private final Clock clock;
    private final String appName;

    SystemController(Clock nptClock,
                     org.springframework.core.env.Environment env) {
        this.clock = nptClock;
        this.appName = env.getProperty("spring.application.name", "backend");
    }

    @GetMapping("/ping")
    @Operation(summary = "Ping", description = "Returns service name, status and current NPT time.")
    PingResponse ping() {
        return new PingResponse(appName, "UP", OffsetDateTime.now(clock), NepalClock.ZONE.getId());
    }

    /** Response body for {@code GET /api/v1/system/ping}. */
    record PingResponse(String service, String status, OffsetDateTime time, String zone) {}
}
