package np.com.thapanarayan.backend.backtest.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BacktestProperties.class)
class BacktestConfig {
}
