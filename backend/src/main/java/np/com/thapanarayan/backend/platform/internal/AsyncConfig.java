package np.com.thapanarayan.backend.platform.internal;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables {@code @Async} and supplies the bounded "pipeline" executor that the
 * post-market-data stages run on. The EOD chain used to run synchronously on the
 * publishing thread (ingestion → market data → indicators → signals); the indicator
 * and signal stages now dispatch here so, once {@code MarketDataReadyEvent} commits,
 * they run concurrently and independently of each other.
 *
 * <p>This executor is the application's default {@code @Async} executor. It is bounded
 * with a {@link ThreadPoolExecutor.CallerRunsPolicy} so a backfill that floods the
 * queue applies back-pressure to the caller rather than dropping work or growing
 * memory without limit.</p>
 */
@Configuration
@EnableAsync
class AsyncConfig implements AsyncConfigurer {

    static final String PIPELINE_EXECUTOR = "pipelineExecutor";

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(PIPELINE_EXECUTOR)
    ThreadPoolTaskExecutor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core 2 lets the indicator and signal stages for one date run in parallel;
        // max 4 absorbs a little overlap across dates during a backfill.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("pipeline-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Let in-flight stages finish on shutdown so a date isn't left half-processed.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        // @Configuration (CGLIB) returns the cached singleton, not a new executor.
        return pipelineExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error("Async task {} failed", method.getName(), ex);
    }
}
