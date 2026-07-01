package np.com.thapanarayan.backend.indicator.api;

import java.time.LocalDate;
import java.util.List;

/**
 * Published entry point to the indicator engine — the catalog and per-symbol compute — so other
 * modules (e.g. charting) can request overlays without touching the engine's internals.
 */
public interface IndicatorEngine {

    List<IndicatorDescriptor> catalog();

    IndicatorResult compute(String id, String symbol, LocalDate from, LocalDate to, ParamValues params);
}
