package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Published port for the indicator catalog and ad-hoc parametrized series. The
 * charting module (Stage 7) composes overlays through this, never reaching into the
 * indicator {@code internal} package.
 */
public interface IndicatorSeriesQuery {

    /** The available indicators with their default parameters. */
    List<IndicatorCatalogEntry> catalog();

    /**
     * Computes an indicator series over {@code [from, to]} (warmed up beyond
     * {@code from} internally). {@code params} may be {@code null}/empty to use the
     * indicator's defaults. Multi-output indicators return several named lines.
     */
    IndicatorSeriesView computeSeries(String symbol, String indicator, List<Integer> params,
            LocalDate from, LocalDate to);
}
