package np.com.thapanarayan.backend.notification.internal;

import java.security.Principal;
import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP {@code CONNECT} frame with the same JWT used for REST
 * (§10.9, §11). The client sends {@code Authorization: Bearer <access token>} in the
 * CONNECT headers; this validates it and binds the user id as the session principal,
 * so {@code convertAndSendToUser(userId, ...)} routes to the right client. An absent
 * or invalid token is rejected — the WebSocket is not an unauthenticated side door.
 */
@Component
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String userId = authenticate(accessor.getNativeHeader("Authorization"));
        accessor.setUser(() -> userId); // Principal#getName() == userId, used by user destinations
        return message;
    }

    private String authenticate(List<String> authHeaders) {
        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new IllegalArgumentException("Missing Authorization on STOMP CONNECT");
        }
        String header = authHeaders.getFirst();
        if (header == null || !header.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Malformed Authorization on STOMP CONNECT");
        }
        Jwt jwt = jwtDecoder.decode(header.substring(7)); // throws on invalid/expired signature
        return jwt.getSubject();
    }
}
