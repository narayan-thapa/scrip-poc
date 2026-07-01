package np.com.thapanarayan.backend.marketdata.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import np.com.thapanarayan.backend.TestcontainersConfiguration;
import np.com.thapanarayan.backend.ingestion.internal.IngestionService;
import np.com.thapanarayan.backend.ingestion.internal.domain.IngestionJob;
import np.com.thapanarayan.backend.marketdata.internal.dao.DailyCandleDao;
import np.com.thapanarayan.backend.marketdata.internal.dao.MarketAggregateDao;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Ingest → aggregate → candle + NEPSE aggregate, against a real Postgres. Tagged {@code integration}. */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MarketAggregationIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 3);

    private static final String CSV = String.join("\n",
            "BHCL, 41, 58, 100, 100, 10000, 2026-06-03 11:00:00:000 AM, 2026060301030001",
            "BHCL, 41, 58, 100, 110, 11000, 2026-06-03 11:05:00:000 AM, 2026060301030002",
            "NABIL, 5, 9, 10, 1000, 10000, 2026-06-03 11:10:00:000 AM, 2026060301030003");

    @Autowired IngestionService ingestionService;
    @Autowired AggregationService aggregationService;
    @Autowired DailyCandleDao candles;
    @Autowired MarketAggregateDao aggregates;

    @Test
    void aggregatesCandlesAndMarketTotals() {
        IngestionJob job = ingestionService.createJob(null, DATE, "2026-06-03.csv", CSV.getBytes(StandardCharsets.UTF_8));
        ingestionService.processJob(job.getId(), true);

        aggregationService.aggregate(DATE, true);

        var bhcl = candles.find("BHCL", DATE, DATE);
        assertThat(bhcl).hasSize(1);
        assertThat(bhcl.get(0).open()).isEqualByComparingTo("100");
        assertThat(bhcl.get(0).close()).isEqualByComparingTo("110");
        assertThat(bhcl.get(0).high()).isEqualByComparingTo("110");
        assertThat(bhcl.get(0).volume()).isEqualTo(200);

        var agg = aggregates.find(DATE).orElseThrow();
        assertThat(agg.totalTrades()).isEqualTo(3);
        assertThat(agg.totalVolume()).isEqualTo(210);  // 200 BHCL + 10 NABIL
        // No prev close → changePct null → both scrips counted as unchanged.
        assertThat(agg.advances() + agg.declines() + agg.unchanged()).isEqualTo(2);
    }
}
