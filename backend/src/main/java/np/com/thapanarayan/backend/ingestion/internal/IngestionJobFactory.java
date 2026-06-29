package np.com.thapanarayan.backend.ingestion.internal;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds and caches the ingestion {@link Job}. Deliberately NOT a {@code @Bean}
 * of type {@code Job}: Boot's {@code JobLauncherApplicationRunner} auto-runs every
 * {@code Job} bean at startup, which we must avoid — the job is launched explicitly
 * by {@link IngestionService}. The step references step-scoped proxies that re-bind
 * to each launch's job parameters, so one cached instance is safely reused.
 */
@Component
class IngestionJobFactory {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final FlatFileItemReader<String> reader;
    private final TradeItemProcessor processor;
    private final FloorsheetTradeWriter writer;
    private final IngestionStepListener listener;
    private final IngestionProperties properties;

    private volatile Job job;

    IngestionJobFactory(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            FlatFileItemReader<String> reader,
            TradeItemProcessor processor,
            FloorsheetTradeWriter writer,
            IngestionStepListener listener,
            IngestionProperties properties) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.reader = reader;
        this.processor = processor;
        this.writer = writer;
        this.listener = listener;
        this.properties = properties;
    }

    Job ingestionJob() {
        Job local = job;
        if (local == null) {
            local = build();
        }
        return local;
    }

    private synchronized Job build() {
        if (job != null) {
            return job;
        }
        Step step = new StepBuilder("ingestStep", jobRepository)
                .<String, ParsedTrade>chunk(properties.chunkSize())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .transactionManager(transactionManager)
                .faultTolerant()
                .skipLimit(Integer.MAX_VALUE)
                .skip(RowRejectedException.class)
                .skip(DuplicateRowException.class)
                .skipListener(listener)
                .listener((StepExecutionListener) listener)
                .build();
        job = new JobBuilder("ingestionJob", jobRepository)
                .start(step)
                .build();
        return job;
    }
}
