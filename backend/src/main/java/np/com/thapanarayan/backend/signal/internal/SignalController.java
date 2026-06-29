package np.com.thapanarayan.backend.signal.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.signal.api.SignalAction;
import np.com.thapanarayan.backend.signal.api.SignalView;

/**
 * Read API for signals plus admin generation triggers (§10.6).
 *
 * <p>TODO(Stage 8 / IAM): gate the generation triggers with role ADMIN once Spring
 * Security is wired; today they are unauthenticated.</p>
 */
@RestController
@RequestMapping("/api/v1/signals")
class SignalController {

    private final SignalQueryService query;
    private final SignalGenerationService generation;

    SignalController(SignalQueryService query, SignalGenerationService generation) {
        this.query = query;
        this.generation = generation;
    }

    /** The latest generated date's signals, optionally filtered by action. */
    @GetMapping("/latest")
    List<SignalView> latest(@RequestParam(required = false) SignalAction action) {
        return query.latest(Optional.ofNullable(action));
    }

    @GetMapping("/{id}")
    SignalView byId(@PathVariable UUID id) {
        return query.findById(id).orElseThrow(() -> new NotFoundException("No signal " + id));
    }

    @GetMapping("/symbol/{symbol}/latest")
    SignalView latestForSymbol(@PathVariable String symbol) {
        return query.latestForSymbol(symbol)
                .orElseThrow(() -> new NotFoundException("No signal for " + symbol));
    }

    @GetMapping("/symbol/{symbol}")
    List<SignalView> forSymbol(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return query.forSymbol(symbol, from, to);
    }

    /** Admin: (re)generate all signals for a date. */
    @PostMapping("/generate")
    ResponseEntity<GenerationResponse> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int created = generation.generateForDate(date, false);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new GenerationResponse(date, created));
    }

    /** Admin: (re)generate a single symbol's signal for a date. */
    @PostMapping("/{symbol}/generate")
    ResponseEntity<SignalView> generateForSymbol(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        SignalView view = generation.generateForSymbol(symbol, date)
                .orElseThrow(() -> new NotFoundException(
                        "No market data to generate a signal for " + symbol + " on " + date));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view);
    }

    record GenerationResponse(LocalDate tradeDate, int signalsCreated) {
    }
}
