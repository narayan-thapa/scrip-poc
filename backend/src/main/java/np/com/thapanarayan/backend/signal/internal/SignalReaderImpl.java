package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import np.com.thapanarayan.backend.signal.api.SignalReader;
import np.com.thapanarayan.backend.signal.api.SignalView;
import org.springframework.stereotype.Service;

@Service
class SignalReaderImpl implements SignalReader {

    private final SignalDao signals;

    SignalReaderImpl(SignalDao signals) {
        this.signals = signals;
    }

    @Override
    public Map<String, SignalView> byDate(LocalDate date) {
        Map<String, SignalView> map = new LinkedHashMap<>();
        for (SignalRecord s : signals.byDate(date, null, null)) {
            map.put(s.symbol(), new SignalView(s.id().toString(), s.symbol(), s.action(), s.score()));
        }
        return map;
    }
}
