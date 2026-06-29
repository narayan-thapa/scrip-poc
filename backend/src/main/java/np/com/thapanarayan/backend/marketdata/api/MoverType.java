package np.com.thapanarayan.backend.marketdata.api;

/** Which movers list to return for a trade date. */
public enum MoverType {
    /** Largest positive change% vs previous close. */
    GAINERS,
    /** Largest negative change% vs previous close. */
    LOSERS,
    /** Highest turnover (most actively traded by value). */
    ACTIVE
}
