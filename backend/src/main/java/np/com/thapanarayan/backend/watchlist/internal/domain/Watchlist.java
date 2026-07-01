package np.com.thapanarayan.backend.watchlist.internal.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A user's named watchlist with its symbols (stored in {@code watchlist_item}). */
@Entity
@Table(name = "watchlist")
public class Watchlist {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "watchlist_item", joinColumns = @JoinColumn(name = "watchlist_id"))
    @Column(name = "symbol")
    private Set<String> symbols = new LinkedHashSet<>();

    protected Watchlist() {
    }

    public Watchlist(UUID userId, String name) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getSymbols() {
        return symbols;
    }

    public void addSymbol(String symbol) {
        symbols.add(symbol.toUpperCase());
    }

    public void removeSymbol(String symbol) {
        symbols.remove(symbol.toUpperCase());
    }
}
