package np.com.thapanarayan.backend.platform.internal.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import np.com.thapanarayan.backend.platform.api.error.ApiError;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-IP rate limit on the auth endpoints (brute-force / credential-stuffing defense). On limit,
 * returns 429 with the canonical {@link ApiError} and a {@code Retry-After} hint. Registered ahead of
 * the security chain so rejected requests never reach authentication.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final ObjectMapper mapper;

    public RateLimitFilter(RateLimiter limiter, ObjectMapper mapper) {
        this.limiter = limiter;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (limiter.allowAuth(clientIp(request))) {
            chain.doFilter(request, response);
            return;
        }
        ApiError error = ApiError.of(OffsetDateTime.now(), HttpStatus.TOO_MANY_REQUESTS.value(),
                ErrorCode.RATE_LIMITED.name(), "Too many requests — slow down.", request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "5");
        response.getWriter().write(mapper.writeValueAsString(error));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
