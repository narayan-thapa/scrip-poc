/**
 * smc module — api package.
 *
 * <p>Smart Money Concepts (SMC) structural analysis: market-structure breaks
 * (BOS/CHoCH), order blocks, and fair-value gaps derived from daily candles.
 * Unlike the numeric indicator engine, SMC outputs price/time <em>zones</em> and
 * discrete structural <em>events</em>, not continuous line series.</p>
 *
 * <p>The {@code api} package is the module's published surface; {@code internal} is
 * implementation detail that no other module may reference (enforced by ArchUnit).</p>
 */
package np.com.thapanarayan.backend.smc.api;
