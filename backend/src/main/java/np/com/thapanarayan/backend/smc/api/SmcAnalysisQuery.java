package np.com.thapanarayan.backend.smc.api;

import java.time.LocalDate;

/**
 * Published read surface for Smart Money Concepts analysis. The charting module
 * depends on this interface, never on the smc {@code internal} package.
 */
public interface SmcAnalysisQuery {

    /**
     * Detect SMC zones and structural breaks for {@code symbol} over the inclusive
     * range {@code [from, to]}. Returns an empty (non-null) analysis when there is
     * insufficient data.
     */
    SmcView analyze(String symbol, LocalDate from, LocalDate to);
}
