package np.com.thapanarayan.backend.platform.internal.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/** Registers the auth rate-limit filter ahead of the security chain (only when enabled). */
@Configuration
class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(prefix = "platform.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter, ObjectMapper mapper) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(new RateLimitFilter(limiter, mapper));
        reg.addUrlPatterns("/api/v1/auth/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE); // before Spring Security
        return reg;
    }
}
