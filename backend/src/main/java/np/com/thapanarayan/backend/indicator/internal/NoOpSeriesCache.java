package np.com.thapanarayan.backend.indicator.internal;

import java.util.function.Supplier;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Default cache: always recompute. A Redis impl can replace this by defining a {@link SeriesCache} bean. */
@Configuration
class NoOpSeriesCacheConfig {

    @Bean
    @ConditionalOnMissingBean(SeriesCache.class)
    SeriesCache noOpSeriesCache() {
        return new SeriesCache() {
            @Override
            public IndicatorResult getOrCompute(String key, Supplier<IndicatorResult> compute) {
                return compute.get();
            }
        };
    }
}
