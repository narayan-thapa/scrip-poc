package np.com.thapanarayan.backend.iam.internal;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limit on the auth endpoints (§11), backed by a Redis counter with a
 * one-minute sliding window. Throttling credential stuffing / brute force.
 *
 * <p>Fails <em>open</em> if Redis is unavailable: rate limiting is defense-in-depth
 * layered on top of Argon2id + generic credential errors, so a Redis outage must not
 * lock every user out of logging in. The degradation is logged.</p>
 */
@Component
class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private final StringRedisTemplate redis;
    private final SecurityProperties properties;

    AuthRateLimitFilter(StringRedisTemplate redis, SecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isThrottled(request)) {
            tooManyRequests(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isThrottled(HttpServletRequest request) {
        if (!isAuthAttempt(request)) {
            return false;
        }
        String key = "ratelimit:auth:" + clientIp(request);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(1));
            }
            return count != null && count > properties.loginRateLimitPerMinute();
        } catch (RuntimeException redisDown) {
            log.warn("Auth rate-limit check skipped (Redis unavailable): {}", redisDown.getMessage());
            return false; // fail open
        }
    }

    private static boolean isAuthAttempt(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static void tooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many attempts. Try again shortly.\"}");
    }
}
