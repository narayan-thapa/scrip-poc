package np.com.thapanarayan.backend.reference.internal;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.platform.api.NotFoundException;
import np.com.thapanarayan.backend.platform.api.PageResponse;
import np.com.thapanarayan.backend.reference.api.InstrumentStatus;
import np.com.thapanarayan.backend.reference.api.InstrumentView;

@RestController
@RequestMapping("/api/v1")
@Validated
class InstrumentController {

    private final InstrumentService instrumentService;

    InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping("/instruments")
    PageResponse<InstrumentView> list(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) InstrumentStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        var pageable = PageRequest.of(page, size, Sort.by("symbol").ascending());
        return PageResponse.from(instrumentService.search(sector, status, q, pageable));
    }

    @GetMapping("/instruments/{symbol}")
    InstrumentView get(@PathVariable String symbol) {
        return instrumentService.findBySymbol(symbol)
                .orElseThrow(() -> new NotFoundException("Unknown instrument: " + symbol));
    }

    @GetMapping("/sectors")
    List<String> sectors() {
        return instrumentService.sectors();
    }
}
