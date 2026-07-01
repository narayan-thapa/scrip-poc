package np.com.thapanarayan.backend.watchlist.internal.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import np.com.thapanarayan.backend.watchlist.internal.WatchlistService;
import np.com.thapanarayan.backend.watchlist.internal.domain.Watchlist;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User watchlists (F8). All operations are scoped to the authenticated user. */
@RestController
@RequestMapping("/api/v1/watchlists")
@Tag(name = "Watchlists", description = "User watchlists")
class WatchlistController {

    private final WatchlistService service;

    WatchlistController(WatchlistService service) {
        this.service = service;
    }

    record WatchlistDto(String id, String name, List<String> symbols) {
        static WatchlistDto from(Watchlist w) {
            return new WatchlistDto(w.getId().toString(), w.getName(), List.copyOf(w.getSymbols()));
        }
    }

    record NameRequest(@NotBlank String name) {}

    record SymbolRequest(@NotBlank String symbol) {}

    @GetMapping
    List<WatchlistDto> list(@AuthenticationPrincipal Jwt jwt) {
        return service.forUser(userId(jwt)).stream().map(WatchlistDto::from).toList();
    }

    @PostMapping
    WatchlistDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody NameRequest req) {
        return WatchlistDto.from(service.create(userId(jwt), req.name()));
    }

    @PutMapping("/{id}")
    WatchlistDto rename(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody NameRequest req) {
        return WatchlistDto.from(service.rename(userId(jwt), id, req.name()));
    }

    @DeleteMapping("/{id}")
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        service.delete(userId(jwt), id);
    }

    @PostMapping("/{id}/items")
    WatchlistDto addItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody SymbolRequest req) {
        return WatchlistDto.from(service.addItem(userId(jwt), id, req.symbol()));
    }

    @DeleteMapping("/{id}/items/{symbol}")
    WatchlistDto removeItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable String symbol) {
        return WatchlistDto.from(service.removeItem(userId(jwt), id, symbol));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
