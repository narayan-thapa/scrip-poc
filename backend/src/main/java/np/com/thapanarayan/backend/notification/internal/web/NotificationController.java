package np.com.thapanarayan.backend.notification.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import np.com.thapanarayan.backend.notification.internal.NotificationDto;
import np.com.thapanarayan.backend.notification.internal.NotificationService;
import np.com.thapanarayan.backend.platform.api.page.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Notification feed + read state + the realtime SSE stream. */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "In-app notification feed + SSE stream")
class NotificationController {

    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    record UnreadCount(long unread) {}

    @GetMapping
    PageResponse<NotificationDto> feed(@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 30) Pageable pageable) {
        return service.feed(userId(jwt), pageable);
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCount(service.unreadCount(userId(jwt)));
    }

    @PatchMapping("/{id}/read")
    void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.markRead(userId(jwt), id);
    }

    @PostMapping("/read-all")
    void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(userId(jwt));
    }

    /** SSE stream (consumed by fetch-event-source with the bearer token). */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        return service.subscribe(userId(jwt));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
