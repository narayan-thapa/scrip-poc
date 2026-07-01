package np.com.thapanarayan.backend.indicator.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult;
import np.com.thapanarayan.backend.indicator.api.IndicatorResult.Point;
import np.com.thapanarayan.backend.indicator.api.OutputKind;
import np.com.thapanarayan.backend.indicator.api.ParamSpec;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Config-composed studies (security Decision E, option 1): new studies built from existing building
 * blocks via <b>validated JSON</b>, never code. Phase 4 ships the {@code MA_CROSS} template
 * (fast/slow moving-average pair). Params are allow-listed and range-checked; nothing is eval'd.
 */
@Component
public class ConfigComposedStudies {

    private static final Pattern ID = Pattern.compile("^[a-z0-9][a-z0-9-]{1,40}$");

    /** A validated definition. Only the MA_CROSS template exists for now. */
    public record Definition(String id, String name, String template, int fast, int slow) {}

    private final Map<String, Definition> definitions = new LinkedHashMap<>();

    /** Validate + register a definition (admin). Throws {@link ApiException} on invalid input. */
    public IndicatorDescriptor register(Definition def) {
        if (def == null || def.id() == null || !ID.matcher(def.id()).matches()) {
            throw bad("id must match " + ID.pattern());
        }
        if (!"MA_CROSS".equals(def.template())) {
            throw bad("Unsupported template: " + def.template());
        }
        if (def.fast() < 2 || def.fast() > 400 || def.slow() < 2 || def.slow() > 400 || def.fast() >= def.slow()) {
            throw bad("Require 2 ≤ fast < slow ≤ 400");
        }
        definitions.put(def.id(), def);
        return descriptorOf(def);
    }

    public boolean contains(String id) {
        return definitions.containsKey(id);
    }

    public List<IndicatorDescriptor> descriptors() {
        return definitions.values().stream().map(ConfigComposedStudies::descriptorOf).toList();
    }

    public IndicatorResult compute(String id, BarSeries series) {
        Definition def = definitions.get(id);
        if (def == null) {
            throw ApiException.notFound("Unknown study: " + id);
        }
        var close = new ClosePriceIndicator(series);
        var fast = new EMAIndicator(close, def.fast());
        var slow = new EMAIndicator(close, def.slow());
        Map<String, List<Point>> lines = new LinkedHashMap<>();
        lines.put("fast", points(series, fast));
        lines.put("slow", points(series, slow));
        return new IndicatorResult.Lines(lines);
    }

    private static IndicatorDescriptor descriptorOf(Definition def) {
        return new IndicatorDescriptor(def.id(), def.name(), "Custom (config)", OutputKind.BAND,
                List.of(ParamSpec.intParam("fast", def.fast(), 2, 400),
                        ParamSpec.intParam("slow", def.slow(), 2, 400)), true);
    }

    private static List<Point> points(BarSeries series, org.ta4j.core.Indicator<Num> ind) {
        List<Point> out = new ArrayList<>();
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Num v = ind.getValue(i);
            if (!v.isNaN()) {
                out.add(new Point(SeriesTimes.at(series, i), v.doubleValue()));
            }
        }
        return out;
    }

    private static ApiException bad(String msg) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, msg);
    }
}
