package np.com.thapanarayan.backend.indicator.api;

import org.ta4j.core.BarSeries;

/**
 * Plugin SPI for an extensible study (§6.5). Implement + register as a Spring {@code @Component};
 * the registry discovers it at startup and serves it in the catalog. Adding a study later requires
 * no engine/API/UI changes — the descriptor's param schema drives the generic settings form.
 */
public interface CustomIndicator {

    IndicatorDescriptor descriptor();

    IndicatorResult compute(BarSeries series, ParamValues params);
}
