package np.com.thapanarayan.backend.signal.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.internal.SignalDao;
import np.com.thapanarayan.backend.signal.internal.SignalEngine;
import np.com.thapanarayan.backend.signal.internal.web.SignalDtos.GenerateRequest;
import np.com.thapanarayan.backend.signal.internal.web.SignalDtos.SignalDetail;
import np.com.thapanarayan.backend.signal.internal.web.SignalDtos.SignalSummary;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read signals + (admin) re-generate. Reasons/votes are first-class for the "why" UI (§F5). */
@RestController
@RequestMapping("/api/v1/signals")
@Tag(name = "Signals", description = "Daily BUY/SELL/HOLD with structured reasons")
class SignalController {

    private final SignalDao signals;
    private final SignalEngine engine;

    SignalController(SignalDao signals, SignalEngine engine) {
        this.signals = signals;
        this.engine = engine;
    }

    @GetMapping("/latest")
    List<SignalSummary> latest(@RequestParam(required = false) SignalAction action) {
        return signals.latestDate()
                .map(date -> signals.byDate(date, action, null).stream().map(SignalSummary::from).toList())
                .orElseGet(List::of);
    }

    @GetMapping
    List<SignalSummary> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                             @RequestParam(required = false) SignalAction action,
                             @RequestParam(required = false) Double minScore) {
        return signals.byDate(date, action, minScore).stream().map(SignalSummary::from).toList();
    }

    @GetMapping("/{id}")
    SignalDetail detail(@PathVariable UUID id) {
        return signals.byId(id).map(SignalDetail::from)
                .orElseThrow(() -> ApiException.notFound("Unknown signal: " + id));
    }

    @GetMapping("/symbol/{symbol}")
    List<SignalSummary> history(@PathVariable String symbol,
                                @RequestParam(defaultValue = "90") int limit) {
        return signals.bySymbol(symbol, limit).stream().map(SignalSummary::from).toList();
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<Void> generate(@RequestBody GenerateRequest req) {
        engine.generate(LocalDate.parse(req.date()), true);
        return ResponseEntity.accepted().build();
    }
}
