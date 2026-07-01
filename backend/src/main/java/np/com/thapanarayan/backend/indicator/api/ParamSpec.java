package np.com.thapanarayan.backend.indicator.api;

import java.util.List;

/**
 * A single configurable parameter. The frontend builds the "Add indicator" settings form from these,
 * and values are validated/bounded server-side before compute (allow-listed; no code).
 */
public record ParamSpec(
        String name,
        ParamType type,
        Object defaultValue,
        Object min,
        Object max,
        List<String> options) {

    public enum ParamType {
        INT,
        DOUBLE,
        BOOLEAN,
        STRING,
        ENUM
    }

    public static ParamSpec intParam(String name, int def, int min, int max) {
        return new ParamSpec(name, ParamType.INT, def, min, max, List.of());
    }

    public static ParamSpec doubleParam(String name, double def, double min, double max) {
        return new ParamSpec(name, ParamType.DOUBLE, def, min, max, List.of());
    }

    public static ParamSpec boolParam(String name, boolean def) {
        return new ParamSpec(name, ParamType.BOOLEAN, def, null, null, List.of());
    }
}
