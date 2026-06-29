package np.com.thapanarayan.backend.reference.internal;

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
import np.com.thapanarayan.backend.reference.api.BrokerView;

@RestController
@RequestMapping("/api/v1/brokers")
@Validated
class BrokerController {

    private final BrokerService brokerService;

    BrokerController(BrokerService brokerService) {
        this.brokerService = brokerService;
    }

    @GetMapping
    PageResponse<BrokerView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        var pageable = PageRequest.of(page, size, Sort.by("brokerId").ascending());
        return PageResponse.from(brokerService.findAll(pageable));
    }

    @GetMapping("/{id}")
    BrokerView get(@PathVariable int id) {
        return brokerService.findById(id)
                .orElseThrow(() -> new NotFoundException("Unknown broker: " + id));
    }
}
