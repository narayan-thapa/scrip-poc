package np.com.thapanarayan.backend.indicator.api;

import java.util.Map;

/** Validated parameter values passed to a study, with typed accessors + defaults. */
public final class ParamValues {

    private final Map<String, Object> values;

    public ParamValues(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    public static ParamValues empty() {
        return new ParamValues(Map.of());
    }

    public int getInt(String name, int def) {
        Object v = values.get(name);
        return v == null ? def : ((Number) v).intValue();
    }

    public double getDouble(String name, double def) {
        Object v = values.get(name);
        return v == null ? def : ((Number) v).doubleValue();
    }

    public boolean getBoolean(String name, boolean def) {
        Object v = values.get(name);
        return v == null ? def : (v instanceof Boolean b ? b : Boolean.parseBoolean(v.toString()));
    }

    public String getString(String name, String def) {
        Object v = values.get(name);
        return v == null ? def : v.toString();
    }
}
