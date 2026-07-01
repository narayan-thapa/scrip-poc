package np.com.thapanarayan.backend.notification.internal;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** On startup, push any notifications a crash left unsent (the outbox survives restarts). */
@Component
@Order(200)
class NotificationRecovery implements ApplicationRunner {

    private final NotificationService service;

    NotificationRecovery(NotificationService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.pushUnsent();
    }
}
