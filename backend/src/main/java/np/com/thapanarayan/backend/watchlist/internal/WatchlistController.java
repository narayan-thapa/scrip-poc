package np.com.thapanarayan.backend.watchlist.internal;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import np.com.thapanarayan.backend.iam.api.CurrentUserProvider;
import np.com.thapanarayan.backend.watchlist.api.WatchlistView;

/** Watchlist CRUD (§9), scoped to the authenticated user. */
@RestController
@RequestMapping("/api/v1/watchlists")
class WatchlistController {

    private final WatchlistService service;
    private final CurrentUserProvider currentUser;

    WatchlistController(WatchlistService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    List<WatchlistView> list() {
        return service.list(currentUser.currentUserId());
    }

    @GetMapping("/{id}")
    WatchlistView get(@PathVariable UUID id) {
        return service.get(currentUser.currentUserId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WatchlistView create(@Valid @RequestBody NameRequest body) {
        return service.create(currentUser.currentUserId(), body.name());
    }

    @PutMapping("/{id}")
    WatchlistView rename(@PathVariable UUID id, @Valid @RequestBody NameRequest body) {
        return service.rename(currentUser.currentUserId(), id, body.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        service.delete(currentUser.currentUserId(), id);
    }

    @PostMapping("/{id}/items")
    WatchlistView addItem(@PathVariable UUID id, @Valid @RequestBody SymbolRequest body) {
        return service.addItem(currentUser.currentUserId(), id, body.symbol());
    }

    @DeleteMapping("/{id}/items/{symbol}")
    WatchlistView removeItem(@PathVariable UUID id, @PathVariable String symbol) {
        return service.removeItem(currentUser.currentUserId(), id, symbol);
    }

    record NameRequest(@NotBlank @Size(max = 64) String name) {
    }

    record SymbolRequest(@NotBlank @Size(max = 20) String symbol) {
    }
}
