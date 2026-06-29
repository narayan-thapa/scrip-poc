package np.com.thapanarayan.backend.ingestion.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import np.com.thapanarayan.backend.reference.api.InstrumentCatalog;

/**
 * Parses and validates one raw line, auto-registers unknown symbols as
 * PROVISIONAL, and detects intra-file duplicate contract ids. Step-scoped: the
 * {@code seen}/{@code knownSymbols} sets live for one file execution only.
 *
 * <p>Returning {@code null} filters blank trailing lines (not an error). Throwing
 * {@link RowRejectedException} quarantines a row; {@link DuplicateRowException}
 * marks an in-file repeat — both are configured as skippable on the step.</p>
 */
class TradeItemProcessor implements ItemProcessor<String, ParsedTrade> {

    private final LocalDate filenameDate;
    private final FloorsheetLineParser parser;
    private final BigDecimal amountTolerance;
    private final InstrumentCatalog instruments;

    private final Set<String> seenContractIds = new HashSet<>();
    private final Set<String> knownSymbols = new HashSet<>();

    TradeItemProcessor(LocalDate filenameDate, FloorsheetLineParser parser,
            BigDecimal amountTolerance, InstrumentCatalog instruments) {
        this.filenameDate = filenameDate;
        this.parser = parser;
        this.amountTolerance = amountTolerance;
        this.instruments = instruments;
    }

    @Override
    public ParsedTrade process(String line) {
        if (line == null || line.isBlank()) {
            return null; // filtered, not rejected
        }
        ParsedTrade trade = parser.parse(line, filenameDate, amountTolerance);
        if (!seenContractIds.add(trade.contractId())) {
            throw new DuplicateRowException(trade.contractId());
        }
        // Register each distinct symbol once per file; absorbs the catalog cost.
        if (knownSymbols.add(trade.symbol())) {
            instruments.getOrCreateProvisional(trade.symbol());
        }
        return trade;
    }
}
