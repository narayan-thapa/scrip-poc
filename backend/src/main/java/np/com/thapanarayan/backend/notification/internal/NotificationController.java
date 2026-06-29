package np.com.thapanarayan.backend.notification.internal;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import np.com.thapanarayan.backend.iam.api.CurrentUserProvider;
import np.com.thapanarayan.backend.notification.api.NotificationView;
import np.com.thapanarayan.backend.platform.api.PageResponse;

/**
 * Notification feed + SSE fallback stream (§9), scoped to the authenticated user. The
 * SSE endpoint is authenticated like any other request (the SPA connects with a
 * fetch-based EventSource that carries the Bearer token); STOMP is the primary channel.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService service;
    private final SseRegistry sse;
    private final CurrentUserProvider currentUser;

    NotificationController(NotificationService service, SseRegistry sse, CurrentUserProvider currentUser) {
        this.service = service;
        this.sse = sse;
        this.currentUser = currentUser;
    }

    @GetMapping
    PageResponse<NotificationView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(currentUser.currentUserId(), page, size);
    }

    @GetMapping("/unread-count")
    UnreadCount unreadCount() {
        return new UnreadCount(service.unreadCount(currentUser.currentUserId()));
    }

    @PostMapping("/{id}/read")
    NotificationView markRead(@PathVariable UUID id) {
        return service.markRead(currentUser.currentUserId(), id);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream() {
        return sse.register(currentUser.currentUserId());
    }

    record UnreadCount(long count) {
    }
}
