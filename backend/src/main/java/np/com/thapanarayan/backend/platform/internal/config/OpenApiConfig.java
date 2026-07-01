package np.com.thapanarayan.backend.platform.internal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Base OpenAPI document. springdoc serves the generated spec at {@code /v3/api-docs}; the frontend
 * codegen consumes that to produce typed DTOs (the Phase 0 contract-first handshake).
 */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI platformOpenApi(@Value("${spring.application.name:backend}") String appName) {
        return new OpenAPI()
                .info(new Info()
                        .title("NEPSE Floorsheet Analytics & Signal Platform API")
                        .description("EOD floorsheet analytics, indicators, signals, backtests and screeners.")
                        .version("v1")
                        .license(new License().name("Proprietary")));
    }
}
