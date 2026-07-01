package np.com.thapanarayan.backend.indicator.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import np.com.thapanarayan.backend.indicator.api.CustomIndicator;
import np.com.thapanarayan.backend.indicator.api.IndicatorDescriptor;
import org.springframework.stereotype.Component;

/**
 * Discovers all {@link CustomIndicator} beans at startup and serves the plugin catalog. Adding a
 * study later = drop in a new {@code @Component}; no engine/API/UI changes. Studies can be toggled
 * on/off by an admin (in-memory flag); disabled studies are hidden from the main catalog.
 */
@Component
public class CustomIndicatorRegistry {

    private final Map<String, CustomIndicator> byId = new LinkedHashMap<>();
    private final Map<String, Boolean> enabled = new ConcurrentHashMap<>();

    CustomIndicatorRegistry(List<CustomIndicator> studies) {
        studies.stream()
                .sorted((a, b) -> a.descriptor().id().compareTo(b.descriptor().id()))
                .forEach(s -> byId.put(s.descriptor().id(), s));
        byId.keySet().forEach(id -> enabled.put(id, true));
    }

    /** Enabled plugin descriptors (for the main catalog). */
    public List<IndicatorDescriptor> catalog() {
        return byId.values().stream()
                .filter(s -> isEnabled(s.descriptor().id()))
                .map(CustomIndicator::descriptor)
                .toList();
    }

    /** All plugin descriptors regardless of enabled state (for admin listing). */
    public List<IndicatorDescriptor> all() {
        return byId.values().stream().map(CustomIndicator::descriptor).toList();
    }

    public Optional<CustomIndicator> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    public boolean isEnabled(String id) {
        return enabled.getOrDefault(id, true);
    }

    public void setEnabled(String id, boolean value) {
        if (!byId.containsKey(id)) {
            throw np.com.thapanarayan.backend.platform.api.error.ApiException.notFound("Unknown study: " + id);
        }
        enabled.put(id, value);
    }
}
