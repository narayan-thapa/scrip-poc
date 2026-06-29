package np.com.thapanarayan.backend.ingestion.internal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.PassThroughLineMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;

import np.com.thapanarayan.backend.platform.api.NepseClock;
import np.com.thapanarayan.backend.reference.api.InstrumentCatalog;

/**
 * Step-scoped batch components for single-file ingestion. Each binds to the job
 * parameters set at launch ({@code archivedPath}, {@code tradeDate}, {@code jobId}),
 * so one cached {@code Job}/{@code Step} (built in {@link IngestionJobFactory}) is
 * relaunched per file with the right inputs.
 */
@Configuration
class IngestionBatchConfig {

    @Bean
    @StepScope
    FlatFileItemReader<String> rawLineReader(
            @Value("#{jobParameters['archivedPath']}") String archivedPath) {
        return new FlatFileItemReaderBuilder<String>()
                .name("rawLineReader")
                .resource(new FileSystemResource(archivedPath))
                .encoding(StandardCharsets.UTF_8.name())
                .linesToSkip(1) // header row
                .lineMapper(new PassThroughLineMapper())
                .build();
    }

    @Bean
    @StepScope
    TradeItemProcessor tradeItemProcessor(
            @Value("#{jobParameters['tradeDate']}") String tradeDate,
            FloorsheetLineParser parser,
            IngestionProperties properties,
            InstrumentCatalog instruments) {
        return new TradeItemProcessor(LocalDate.parse(tradeDate), parser,
                properties.amountTolerance(), instruments);
    }

    @Bean
    @StepScope
    FloorsheetTradeWriter floorsheetTradeWriter(
            @Value("#{jobParameters['jobId']}") Long jobId,
            JdbcTemplate jdbcTemplate) {
        return new FloorsheetTradeWriter(jdbcTemplate, jobId);
    }

    @Bean
    @StepScope
    IngestionStepListener ingestionStepListener(
            @Value("#{jobParameters['jobId']}") Long jobId,
            IngestionRejectionRepository rejections,
            IngestionJobRepository jobs,
            NepseClock clock) {
        return new IngestionStepListener(jobId, rejections, jobs, clock);
    }
}
