package np.com.thapanarayan.backend.reference.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import np.com.thapanarayan.backend.platform.api.error.ApiException;
import np.com.thapanarayan.backend.platform.api.page.PageResponse;
import np.com.thapanarayan.backend.reference.internal.domain.InstrumentStatus;
import np.com.thapanarayan.backend.reference.internal.repo.BrokerRepository;
import np.com.thapanarayan.backend.reference.internal.repo.InstrumentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only reference lookups: instruments, sectors, brokers. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reference", description = "Instruments, sectors and brokers")
class ReferenceController {

    private final InstrumentRepository instruments;
    private final BrokerRepository brokers;

    ReferenceController(InstrumentRepository instruments, BrokerRepository brokers) {
        this.instruments = instruments;
        this.brokers = brokers;
    }

    @GetMapping("/instruments")
    PageResponse<InstrumentDto> listInstruments(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) InstrumentStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.from(instruments.search(sector, status, blankToNull(q), pageable), InstrumentDto::from);
    }

    @GetMapping("/instruments/{symbol}")
    InstrumentDto getInstrument(@PathVariable String symbol) {
        return instruments.findById(symbol)
                .map(InstrumentDto::from)
                .orElseThrow(() -> ApiException.notFound("Unknown instrument: " + symbol));
    }

    @GetMapping("/sectors")
    List<String> listSectors() {
        return instruments.findDistinctSectors();
    }

    @GetMapping("/brokers")
    PageResponse<BrokerDto> listBrokers(@PageableDefault(size = 100) Pageable pageable) {
        return PageResponse.from(brokers.findAll(pageable), BrokerDto::from);
    }

    @GetMapping("/brokers/{id}")
    BrokerDto getBroker(@PathVariable Integer id) {
        return brokers.findById(id)
                .map(BrokerDto::from)
                .orElseThrow(() -> ApiException.notFound("Unknown broker: " + id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
