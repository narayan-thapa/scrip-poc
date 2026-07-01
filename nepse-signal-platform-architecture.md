# NEPSE Floorsheet Analytics & Signal Platform
### Architecture & Technical Design

**Stack:** Java 25 (LTS) · Spring Boot 4.0 (Spring Framework 7, Jakarta EE 11) · Spring Batch · PostgreSQL/TimescaleDB · Redis · Angular 22 · TradingView Lightweight Charts™ v5

---

## 0. The single most important design decision

Your data is the **NEPSE floorsheet** — every individual matched trade, with buyer broker ID, seller broker ID, quantity, price, and a contract ID. NEPSE publishes this **only after the 3:00 PM close** (hence your 3:01 PM scrape). Three consequences drive the entire architecture:

1. **This is an End-of-Day (EOD) batch system, not a streaming system.** One ingestion + analysis cycle runs per trading day. Signals are *daily* swing/positional signals. There is no live intraday signal generation from this feed — that would require a separate live price feed, which is out of scope here.
2. **You can still reconstruct intraday structure retrospectively.** Millisecond timestamps let you build 1m/5m/15m candles and time-segmented volume profiles *after* the close, for charting and analysis — but not for live alerts.
3. **The broker dimension is a unique asset.** Because every trade names its buyer and seller broker, you can compute **true volume profile** (exact volume-at-price) and **broker accumulation/distribution**, which most platforms can only approximate. These are built in as first-class features and signal inputs.

Everything below assumes a **modular monolith**: one deployable Spring Boot service, internally partitioned into strict modules whose boundaries are enforced by **ArchUnit** tests, with a **Spring Batch** job as the durable pipeline backbone and plain Spring `ApplicationEvent`s for decoupled fan-out. This satisfies your "one monolith for all features" requirement while keeping the codebase decomposable later if you ever split it.

---

## 1. Domain & data model understanding

### 1.1 Raw record anatomy

```
Symbol, Buyer, Seller, Quantity, Price, Amount, Trade Time, Contract Id
BHCL,   41,    58,     2000,     600.8, 1201600, 2026-06-03 2:59:59:988 PM, 2026060301020260
```

| Field | Meaning | Notes |
|---|---|---|
| Symbol | Listed scrip code | Join key to instrument reference |
| Buyer | Buying broker ID | NEPSE numbered broker; used for broker-flow analysis |
| Seller | Selling broker ID | Same |
| Quantity | Shares matched | Integer |
| Price | Matched price | Decimal (e.g. `600.8`, `3595.1`) or integer |
| Amount | Trade value | ≈ Quantity × Price (verify with tolerance) |
| Trade Time | Match timestamp | **12-hour, millisecond precision, non-zero-padded hour** |
| Contract Id | Unique trade ID | `YYYYMMDD` + session/segment + sequence → **natural idempotency key** |

**Verification of Amount:** `2000 × 600.8 = 1,201,600` ✓. Use Amount as a cross-check, not a source of truth — recompute and flag rows where `|Amount − Qty×Price| > 0.5`.

**Contract Id decode:** `2026060301020260` = `20260603` (date) · `01` (market segment / batch) · `020260` (sequence). It is globally unique per trade and is the **primary dedup key** for idempotent re-ingestion.

**Filename convention:** each daily scrape is delivered as `YYYY-MM-DD.csv` (e.g. `2026-01-13.csv`). The filename is the authoritative **trade date** for the file: it is parsed once at intake, used as the partition/idempotency key for the whole day, and **cross-checked** against the date portion of every row's `Trade Time` (a row whose timestamp date ≠ the filename date is quarantined). This convention is what makes safe **multi-file backfill** possible — see F1 and the batch-upload API.

### 1.2 Data-quality hazards (visible in your sample)

The scraped CSV is **untrusted external input** and must be parsed defensively:

- **Inconsistent whitespace** — tabs and spaces after commas (`GRDBL, 63,` vs `GRDBL,63,`).
- **Two timestamp formats** — most rows `2026-06-03 2:59:59:988 PM`; some `2026-06-05, 2:59:59:729 PM` (comma after date).
- **Non-standard time** — `H:mm:ss:SSS a` (colon before millis, single-digit hour, AM/PM). This is **not** ISO-8601 and will break naive parsers.
- **Mixed numeric types** — price integer or decimal; quantity may carry trailing spaces.
- **Possible holiday / empty days** — scraper may return no rows; pipeline must no-op gracefully.
- **CSV/formula injection** — never round-trip raw cells into any spreadsheet/export without neutralizing leading `= + - @`.

**Parsing strategy:** normalize whitespace → strip optional comma after date → attempt an ordered list of `DateTimeFormatter` patterns → validate ranges → dedup on `contract_id` → quarantine rejects (don't fail the whole batch). See §10.3.

### 1.3 What is *derived* from floorsheet

| Derived artifact | How |
|---|---|
| **Daily OHLC** | Per (symbol, date): Open = first trade by time, High = max price, Low = min price, Close = last trade by time |
| **Daily Volume / Turnover / Trades** | Σ quantity / Σ amount / count |
| **VWAP** | Σ amount ÷ Σ quantity |
| **Intraday candles** | Bucket trades by time window (1m/5m/15m) — retrospective only |
| **Volume Profile** | Histogram of Σ quantity per price bin → POC, Value Area, HVN/LVN (exact, not TPO) |
| **Broker Flow** | Per (symbol, date, broker): buy/sell qty & amount, net, concentration |
| **Money Flow** | Typical price × volume, signed by up/down day → MFI, CMF, A/D line |
| **Market aggregate (`NEPSE`)** | Σ over *all* symbols per day → total turnover / volume / trades + breadth (exact). A market **index level** is *not* derivable from trades (it's float-cap-weighted) — only a labeled cap-weighted *proxy* (needs `listed_shares`) or an ingested official index. |

---

## 2. High-level architecture

### 2.1 Module map (modular monolith)

One Spring Boot deployable. Each module is a top-level package with a published API package and an internal implementation package; cross-module access is restricted to the published packages, **enforced by ArchUnit tests that fail the build on a boundary violation**. Modules collaborate two ways: the daily pipeline drives them in order as steps of a **Spring Batch** job, and decoupled side-effects use plain Spring `ApplicationEvent`s — no message broker required.

```
com.nepse.analytics
├── reference        # instruments, sectors, brokers, trading calendar (holidays)
├── ingestion        # CSV intake, validation, dedup, raw-trade store, pipeline trigger
├── marketdata       # candle aggregation, VWAP, volume profile, broker flow
├── indicator        # Ta4j-backed technical-indicator engine (adapts candles → BarSeries)
├── signal           # strategy engine (Ta4j rules) → confluence scoring → signal + reasons
├── backtest         # historical replay, cost model, metrics
├── charting         # composite chart payload assembly (read-only over the above)
├── screener         # read-side scans/rankings: day activity, relative volume, price-drop (over marketdata + indicator + signal)
├── watchlist        # user watchlists / paper portfolio
├── notification     # in-web notifications, alert rules, WebSocket/SSE push
├── iam              # auth, users, roles (Spring Security + Authorization Server)
└── platform         # shared kernel: error model, pagination, time/calendar utils, config
```

> **Why this structure (no extra modularity framework):** ArchUnit enforces the package boundaries at build time — a plain JUnit test, zero runtime dependency. The **Spring Batch JobRepository** gives the pipeline restartability and an audit trail of every run for free. Plain Spring events (`@TransactionalEventListener(phase = AFTER_COMMIT)`) keep modules decoupled for fan-out work like notifications and cache eviction. Standard Spring Boot Actuator + Micrometer cover health and observability. Nothing here needs Kafka or a dedicated modularity library for a single deployable.

### 2.2 Pipeline flow (orchestrated batch + decoupled events)

The daily cycle runs as a **single Spring Batch job** — a sequence of restartable steps — with plain Spring `ApplicationEvent`s used only for decoupled fan-out after signals are committed:

```
[Scheduler 15:01 NPT  OR  scraper webhook]
        │  launches Spring Batch job
        ▼
 Step 1  ingest      ──► parse, validate, dedup → floorsheet_trade
        ▼
 Step 2  aggregate   ──► candles / VWAP / volume profile / broker flow
        ▼
 Step 3  indicators  ──► compute indicator catalog for affected symbols
        ▼
 Step 4  signals     ──► strategies + confluence → BUY/SELL/HOLD + reasons
        │  on AFTER_COMMIT, publishes plain Spring  SignalsGeneratedEvent(date, counts)
        ├────────────► backtest listener      ──► refresh rolling backtests for the BUY list
        └────────────► notification listener   ──► persist + push notifications (WS/SSE)
```

The Batch **JobRepository** persists step-execution state, so if the JVM dies mid-pipeline you **restart the job and it resumes from the failed step** — no message broker, no event-registry library. Notifications are written to the `notification` table inside the notification step, so a crash never loses them; a dispatcher pushes any unsent rows on the next launch. Steps are idempotent — re-running a date replaces that date's derived rows.

### 2.3 Runtime & deployment topology

- **Single Spring Boot 4 process**, packaged as a layered container image. Java 25 lets you run blocking I/O on **virtual threads** (`spring.threads.virtual.enabled=true`) so ingestion and per-symbol indicator computation scale without a reactive rewrite.
- **PostgreSQL** as system of record. Adopt **TimescaleDB** (Postgres extension) *or* native declarative partitioning for the two high-volume tables (`floorsheet_trade`, `intraday_candle`) partitioned by month.
- **Redis** for: computed-series cache, latest-signals cache, rate-limit counters, and WebSocket fan-out backplane if you later run >1 instance.
- **Object storage (S3/MinIO)** to archive every raw scraped CSV (immutable, content-hashed) so any day can be deterministically reprocessed.
- **Scheduler:** in-process `@Scheduled` (or ShedLock if you ever run multiple replicas) for the 15:01 trigger; manual admin trigger also exposed.

### 2.4 Persistence guidelines

- Daily aggregates are tiny (≈ hundreds of symbols × 1 row/day) → keep hot, no partitioning needed.
- Raw trades grow fast (thousands/day × symbols × years) → **partition by month**, index `(symbol, trade_date)`, BRIN index on `trade_time`.
- Indicator outputs: store the canonical set per (symbol, date) as JSONB plus a few promoted columns (close-based RSI/EMA) for fast filtering; **cache ad-hoc/parametrized series in Redis** keyed by `(symbol, indicator, paramsHash, lastDate)`.
- Volume profile: store POC/VAH/VAL + the binned histogram as JSONB per (symbol, window).

---

## 3. The EOD pipeline (orchestration detail)

| Stage | Trigger | Work | Output / event | Idempotency |
|---|---|---|---|---|
| **1. Intake** | 15:01 NPT scheduler **or** `POST /ingestion/uploads[/batch]` **or** `POST /ingestion/trigger` **or** scraper webhook | Validate filename(s), archive raw to object store with content hash; for a batch, sort dates ascending | `RawFileRegistered` (per file) | hash dedup |
| **2. Ingest** | `RawFileRegistered` | Stream-parse, validate, dedup by `contract_id`, persist `floorsheet_trade`, quarantine rejects | `TradesIngestedEvent` | upsert on `contract_id` |
| **3. Aggregate** | `TradesIngestedEvent` | Build daily candle, VWAP, volume profile, broker flow; (optional) intraday candles | `MarketDataReadyEvent` | recompute-on-rerun |
| **4. Indicators** | `MarketDataReadyEvent` | Compute indicator catalog for affected symbols (incremental where possible) | `IndicatorsComputedEvent` | deterministic by input |
| **5. Signals** | `IndicatorsComputedEvent` | Run strategies, aggregate via confluence, persist signal + structured reasons | `SignalsGeneratedEvent` | replace day's signals |
| **6. Backtest refresh** | `SignalsGeneratedEvent` | Roll forward backtests for symbols on the BUY list | `BacktestUpdatedEvent` | versioned runs |
| **7. Notify** | `SignalsGeneratedEvent` | Match user alert rules / watchlists, persist notifications, push via WS/SSE | — | dedup per (user, signal) |

**Spring Batch orchestrates the pipeline** as one restartable job whose steps map to stages 2–5: chunked reads for ingestion, skip/retry policies for dirty rows, and a job repository that audits each run and resumes from the failed step. Each step publishes the plain Spring `ApplicationEvent` shown in the table as an observation point — driving pipeline-status updates and the stage 6–7 fan-out (backtest refresh, notifications) — but Batch, not the events, controls ordering and restart.

---

## 4. Feature catalog (deep specification)

Each feature lists **purpose → inputs → processing → outputs → spec notes**.

### F1. Data Ingestion (admin)
- **Purpose:** Turn untrusted daily CSV(s) into clean, deduplicated, queryable trades — never let one bad row sink the batch, and support both the daily file and bulk historical **backfill** of many `YYYY-MM-DD.csv` files at once.
- **Inputs:** one or more scraped CSVs (single upload, **multi-file batch upload**, watched path, or signed webhook); the trade date is taken from each filename.
- **Processing:** validate filename (`YYYY-MM-DD.csv`) → archive raw under a controlled key → stream parse → normalize → multi-pattern timestamp parse → range/consistency validation (incl. **filename-date vs row-timestamp-date** cross-check) → dedup on `contract_id` → chunked upsert → quarantine rejects with reason codes. For a **batch**, files are sorted **oldest-first** and processed in that order (see below).
- **Outputs:** persisted trades; one **ingestion job record per file** (counts: read/accepted/rejected/duplicate) grouped under a **batch record**; a downloadable rejection report per file.
- **Spec notes:** idempotent (re-uploading a date replaces it, never duplicates — `contract_id` upsert); configurable Amount tolerance; per-file row/size guardrails **and per-batch file-count/total-size caps**; **treats input as hostile** (filename sanitized — never used as a filesystem path; no eval; formula-injection neutralized; bounded memory via streaming).

**Batch / backfill processing order (why ordering matters).** Indicators and signals for day *N* depend on days *N−1, N−2, …* (look-back windows), so a batch is **never** processed in upload order — it is processed by ascending trade date. The phases are:
1. **Intake** (synchronous, fast): validate filenames, reject malformed names and duplicate dates within the batch, enforce caps, archive each file, create the batch + per-file jobs, return `202 Accepted` with a `batchId`. A file whose date is a known non-trading day is flagged (configurable warn/reject).
2. **Ingest** (async, parallel across files — rows are independent, `contract_id` dedup absorbs any overlap): parse/validate/upsert raw trades.
3. **Aggregate** (parallel per symbol×date): candles / VWAP / volume profile / broker flow for every date in the batch range.
4. **Indicators** then **Signals** (ordered by date ascending, parallel across symbols): each date's Ta4j `BarSeries` is built from history up to that date. The earliest backfilled dates may have signals **suppressed until the indicator warm-up window is satisfied** (e.g. a 200-period EMA needs ~200 prior bars), so a backfill should include enough lead-in history before the first date you actually care about.
5. **Notifications are NOT fanned out for backfilled historical dates** — only the most recent trading day (or the live daily run) notifies users; otherwise a backfill would spam months of stale alerts.

The daily 15:01 NPT run is simply the degenerate case of this pipeline with a one-date range; backfill reuses the same Spring Batch steps with a date-range driver.

### F2. Reference & Trading Calendar
- **Purpose:** Master data for symbols, sectors, brokers, and the NEPSE holiday calendar.
- **Inputs:** seed lists + admin edits; symbols auto-discovered from floorsheet (new symbol → provisional record flagged for enrichment).
- **Processing:** maintain instrument metadata (name, sector, listed shares, status, price band), broker registry, and `trading_day` calendar (so "previous close" and look-back windows skip holidays correctly).
- **Outputs:** reference lookups; calendar-aware date math used everywhere.
- **Spec notes:** every look-back ("14-day RSI", "20-day volume avg") counts **trading days**, not calendar days — sourced from the calendar, not naive date subtraction.

### F3. Market Data Service
- **Purpose:** Derive and serve all price/volume structure.
- **Sub-features & specs:**
  - **Daily candles:** O/H/L/C by trade-time ordering; plus volume, turnover, trades, VWAP, prev_close, change%.
  - **Intraday candles (retrospective):** configurable interval; gaps preserved; used for charts only.
  - **VWAP:** session VWAP per day; optional anchored VWAP from a chosen date for charting.
  - **Volume Profile (exact):** §6.2 — POC, VAH, VAL, HVN/LVN, plus composite (range) profile.
  - **Broker Flow:** §6.3 — per-broker net qty/amount, concentration (top-N share, Herfindahl index), net-buyer/seller leaders.
  - **Market summary / movers:** advances/declines, top gainers/losers/volume/turnover, sector breadth.
  - **Market aggregate (the `NEPSE` instrument):** aggregate *all* of the day's trades (every symbol) into a single chartable series under the reserved symbol `NEPSE` — total **turnover**, total **volume**, total **trades**, and **breadth** (advances/declines/unchanged). These are exact from the floorsheet. A market-wide **broker flow** (biggest net buyers/sellers across the whole market) is also produced here. **Index level (separate concern):** the true NEPSE index is *float-adjusted market-cap weighted* and is **not** the sum of trades, so the price line is an explicit choice — (a) a **cap-weighted index proxy** computed from `price × listed_shares` per company (uses `instrument.listed_shares`; clearly labeled an *approximation* — no free-float/divisor/corporate-action handling), or (b) the **official NEPSE index** ingested from a *separate* feed (the real line, outside the floorsheet). Volume profile is **not** produced for `NEPSE` (no single price axis market-wide); individual scrips are unaffected and keep aggregating from their own trades.
- **Outputs:** JSON series for charting and downstream engines.

### F4. Indicator Engine
- **Purpose:** Compute a broad set of technical indicators over candle series, backed by **Ta4j-core** (130+ indicators) so the standard TA math is library-grade rather than hand-rolled.
- **Catalog:**
  - *Trend:* SMA, EMA, WMA, MACD, ADX/DMI, Supertrend, Parabolic SAR, Ichimoku.
  - *Momentum:* RSI, Stochastic, Stoch-RSI, CCI, Williams %R, ROC, **MFI** (volume-weighted).
  - *Volatility:* Bollinger Bands, ATR, Keltner, Donchian.
  - *Volume:* OBV, Volume SMA, **CMF**, **A/D Line**, VWAP — plus **Volume Profile** (POC/VAH/VAL) and **VPVR**, which are **custom** (not in Ta4j — see Processing).
  - *Levels:* Pivot Points (Classic/Fibonacci/Camarilla), swing highs/lows, volume-profile nodes.
  - *Custom studies (plugin SPI, §6.5):* **HMA**, **UT Bot**, **Inside Hammer**, **SMC** ship in this set, and new studies are added by dropping in a Spring bean — no engine changes.
- **Extensibility:** the catalog is plugin-driven — Ta4j built-ins *plus* a `CustomIndicator` SPI (§6.5). Studies are typed by **output kind** (`LINE`/`BAND`/`SIGNAL`/`MARKER`/`ZONE`) so the chart and signal engine know how to consume them; numeric custom studies expose a Ta4j `Indicator<Num>` view and thus backtest through the same path.
- **Processing:** a thin `IndicatorService` adapts each symbol's `daily_candle` history into a Ta4j `BarSeries` (via `BaseBarSeriesBuilder`), then resolves the requested indicators as Ta4j `Indicator<Num>` instances. Ta4j computes them lazily and caches within the series pass (its `CachedIndicator`s are internally thread-safe). The two floorsheet-native analytics — **volume profile** and **broker flow** — are **not** part of Ta4j and stay in the `marketdata` module as custom code; they feed the signal engine as side inputs alongside the Ta4j indicators.
- **Outputs:** canonical per-(symbol, date) snapshot (JSONB) + cached parametrized series for chart overlays.
- **Spec notes:** parameters are validated and bounded; results are deterministic. **Num precision:** use Ta4j's `DecimalNum` (BigDecimal-backed, matches the `NUMERIC(18,4)` schema) for signal/backtest correctness, and `DoubleNum` for speed on chart overlays where exactness matters less. Two caching layers with distinct scopes: Ta4j caches indicator values **in-memory within a single computation pass**, while Postgres holds the canonical per-(symbol, date) snapshot (JSONB) and Redis caches parametrized series **across requests**, keyed by `(symbol, indicator, paramsHash, lastDate)`.

### F5. Signal Engine
- **Purpose:** Produce a daily **BUY / SELL / HOLD** per scrip **with explicit, human-readable reasons**.
- **Processing:** run N independent strategies (§6.1). Each strategy expresses its conditions as **Ta4j `Rule`s over Ta4j `Indicator`s** (e.g. `CrossedUpIndicatorRule`, `OverIndicatorRule`, combined with `and()`/`or()`), evaluates them at the latest bar, then **maps the boolean rule outcome plus the underlying indicator values into a directional vote ∈ [−1, +1] with a confidence** (derived from distance-past-threshold / slope) and a set of **structured reasons**. A **confluence scorer** combines the weighted votes into a final score ∈ [−100, +100] → action via thresholds → persist signal + reasons + indicator snapshot.
- **Outputs:** per-symbol signal objects; "latest run" BUY/SELL/HOLD lists; per-symbol signal history.
- **Spec notes (reasons are first-class):** every reason is a structured record — `{strategyId, indicator, condition, observedValue, threshold, contribution, narrative}` — rendered to text *and* available as data, so the UI can show "why" and you can audit every signal. Final signal exposes top contributing reasons + dissenting strategies. Note the deliberate split: Ta4j supplies the **indicator math and boolean triggers**; this engine adds the **confidence weighting, confluence, and narrative**, which Ta4j's binary entry/exit model does not provide.

### F6. Backtesting Engine
- **Purpose:** Validate a strategy on history with **realistic NEPSE costs** and **no look-ahead bias**.
- **Inputs:** strategy/config, symbol(s), date range, starting capital, position sizing, cost model.
- **Processing:** §7 — per symbol, run the strategy through Ta4j's **`BarSeriesManager`** (single strategy) or **`BacktestExecutor`** (ranking many strategies/parameters in parallel) over the `BarSeries`, producing a Ta4j `TradingRecord`. Confluence-based entries are handled by wrapping the confluence **score as a custom Ta4j `Indicator`** and entering/exiting with `OverIndicatorRule`/`UnderIndicatorRule` at the ±threshold. Signals are taken as-of each day's *close* and filled at the next trading day's *open*; a custom layer applies the NEPSE cost model and aggregates per-symbol records into a shared-capital portfolio.
- **Outputs:** metrics via Ta4j `AnalysisCriterion`s (return, max drawdown, win ratio, profit factor, position counts) **plus custom criteria** for what Ta4j doesn't ship or must be NEPSE-cost-adjusted (e.g. Sortino, CAGR, exposure) — the criterion interface is extensible; equity & drawdown curves; full trade log with entry/exit reasons.
- **Spec notes:** signal-at-close / fill-next-open prevents peeking (Ta4j's default `TradeOnCurrentCloseModel` is shifted to avoid same-bar look-ahead). **Why a custom layer wraps Ta4j here:** Ta4j's `LinearTransactionCostModel` is a flat percentage and can't express NEPSE's *tiered* brokerage, the flat *per-scrip DP* charge, or *CGT*; and Ta4j operates on a single `BarSeries`, so shared-capital **portfolio** sizing across the BUY list is also custom. Runs are versioned and comparable (`BacktestExecutor` provides weighted normalized leaderboards for parameter sweeps).

### F7. Charting (TradingView)
- **Purpose:** Render each scrip on a **TradingView Lightweight Charts™ v5** chart — candles, indicator overlays/sub-panes, volume, the volume profile, and BUY/SELL/HOLD signal markers — from one backend payload.
- **Inputs:** symbol, interval (`1D` default; intraday `5m`/`15m` retrospective), date range, requested indicators/overlays.
- **Processing:** the `/charts/{symbol}` endpoint assembles a payload whose arrays map **1:1 to Lightweight Charts series**: candlesticks `{time, open, high, low, close}`, indicator lines `{time, value}`, a volume histogram `{time, value, color}`, the volume-profile bins (`{price, volume}` + POC/VAH/VAL), and signal markers `{time, position, shape, color, text}`. Because the data is EOD, `time` is the `"YYYY-MM-DD"` business-day string — no intraday tick stream.
- **Outputs:** chart-ready JSON. The Angular side creates series with `chart.addSeries(CandlestickSeries, …)`, plots each indicator either as an overlay or in its **own pane** (v5 multi-pane — RSI/MACD/volume go below the price pane), attaches markers via the v5 **`createSeriesMarkers(series, markers)`** plugin (v4's `series.setMarkers()` is gone), and renders the **volume profile as a custom series primitive** (Lightweight Charts has no built-in profile) — a right-aligned horizontal histogram with POC/VAH/VAL guides. Clicking a marker opens the signal's reasons panel (§F5).
- **Custom studies render by output kind (§6.5):** `LINE`→ line series (HMA); `BAND`→ multiple lines; `SIGNAL`→ a line + `createSeriesMarkers` (UT Bot stop + buy/sell arrows); `MARKER`→ `createSeriesMarkers` (Inside Hammer); `ZONE`→ custom box/ray/label primitives (SMC order blocks, FVGs, BOS/CHoCH, liquidity). The "Add indicator" dialog is generated from each study's param schema, so any future study appears in the picker automatically.
- **The `NEPSE` symbol (whole-market) charts like any other symbol but with different series (F3).** Its price pane is the **index line** when available — the cap-weighted *proxy* or the *official* index — otherwise the chart leads with **total turnover / volume** as the main series; **breadth** (advances/declines) and **market-wide broker flow** are available panes. **Volume profile is hidden for `NEPSE`** (no market-wide price axis), and indicators run on the index line (proxy/official) when present — e.g. EMA/ADX on `NEPSE` is the market-regime input used by the confluence gate (§6.4). Every individual scrip is unchanged: its chart is built from its own trades.
- **Spec notes:** read-only composition over F3–F5; cache-friendly (ETag). **License:** Lightweight Charts is Apache-2.0 but requires **TradingView attribution** — enable the `attributionLogo` layout option (satisfies the link requirement) or add the NOTICE attribution. **Library choice:** *Lightweight Charts (default)* — free, ~35 kB, you feed it our server-computed data, no approval needed; *Advanced Charts (upgrade)* — adds built-in studies + drawing tools but requires implementing a **Datafeed adapter** (`resolveSymbol`/`getBars`/`searchSymbols`, with `subscribeBars` a no-op given EOD) and requesting access under TradingView's terms. **Not** the hosted TradingView *widgets* — those show TradingView's own data, which doesn't cover NEPSE scrips or our floorsheet-derived series.

### F8. Watchlist / Paper Portfolio
- **Purpose:** Let users track symbols and (optionally) simulate positions.
- **Processing:** CRUD watchlists & items; optional paper trades valued at latest close; ties into alerts.
- **Outputs:** user watchlists, paper P&L.

### F9. Notifications & Alerts
- **Purpose:** Push the day's relevant signals to the user **in the web app**.
- **Processing:** after `SignalsGeneratedEvent`, match each signal against (a) user watchlists and (b) user-defined **alert rules** (e.g. "BUY signal on any watchlist symbol", "RSI < 30 on SYMBOL", "broker-distribution flag on SYMBOL") → persist notifications → push over **WebSocket (STOMP)** with **SSE** fallback.
- **Outputs:** notification feed (read/unread), real-time toasts, alert-rule management.
- **Spec notes:** notifications are persisted in the same step that creates them, then a dispatcher pushes unsent rows (a small purpose-built outbox keyed on the `sent` flag), so delivery survives a crash; dedup per (user, signal); since the data is EOD, "real-time" means "pushed the moment the daily run completes."

### F10. Identity & Access (IAM)
- **Purpose:** AuthN/AuthZ for users and admins.
- **Processing:** registration/login, JWT access + refresh, role-based authorization (`USER`, `ANALYST`, `ADMIN`); admin-only ingestion/strategy endpoints; password hashing with Argon2id (or BCrypt).
- **Outputs:** tokens, current-user profile, role-gated access.
- **Spec notes:** see §11 for the secure-default choices (token storage, CORS, secrets).

### F11. Day Transaction Analysis Dashboard
- **Purpose:** One-glance EOD view of a trading day's activity — what traded most and least, and where volume is unusual relative to normal.
- **Inputs:** date (default latest trading day), baseline window (default 20 trading days), spike/drop thresholds.
- **Processing:** compose breadth/movers/summary (reuse F3) and compute, per scrip, **Relative Volume (RVOL)** and a volume **z-score** against the baseline (§6.6); rank activity by turnover, volume, and trade count.
- **Outputs:** panels — **High trade** (top by turnover / volume / trades) and **Low trade** (thin-liquidity tail by the same); **Relative volume spikes** (RVOL ≥ spikeThreshold or high z-score); **Relative volume drops** (RVOL ≤ dropThreshold); market breadth (advances/declines, sector heat). Every row links to the chart (F7) and shows the scrip's current signal (F5).
- **Spec notes:** windows are **trading days** via the calendar; a scrip needs ≥ N prior sessions of history for RVOL (newly listed → flagged "insufficient history"); scrips that didn't trade today are excluded from spikes and surfaced separately as "no-trade"; the whole dashboard is pre-warmed on `SignalsGeneratedEvent` and Redis-cached, so the page loads instantly.

### F12. Sharp Price-Drop Screener
- **Purpose:** List scrips that have fallen sharply over a chosen window — **default 30 trading days, presets 45 / 60, and a custom value**.
- **Inputs:** window (trading days; default 30; presets 45/60; custom, bounded 5–365); drop **metric** (default `pctchange`; optional `drawdown` from window high; optional `sharpness`, ATR-normalized); threshold; filters (sector, min turnover/volume, min price, exclude newly-listed/suspended); sort; page.
- **Processing:** per scrip over `[date−window, date]` compute %Δ, drawdown-from-window-high, and an ATR-normalized sharpness score (§6.6); rank by the chosen metric.
- **Outputs:** ranked rows — symbol / name / sector, current close, close N-days-ago, **%Δ**, **drawdown-from-high**, **sharpness**, window high/low, volume + **RVOL context** (is the fall on heavy or light volume?), the current **signal** (BUY/SELL/HOLD), and a link to the chart.
- **Spec notes:** windows count **trading days**; a **liquidity floor is on by default** so drops on near-zero-turnover scrips are excluded as noise; the screener is **descriptive** — it shows the system's signal next to the drop so a user can tell a "falling knife" from a mean-reversion candidate; presets 30/45/60 are pre-warmed at EOD and Redis-cached, custom windows are computed on demand.

---

## 5. Technical strategies — overview

The engine ships multiple independent strategies; the confluence layer (§6.4) blends them. Each is configurable and individually backtestable.

| # | Strategy | Core logic | Confirmation |
|---|---|---|---|
| S1 | **Trend-following** | EMA(fast) crosses EMA(slow) | ADX > 20–25 filters chop |
| S2 | **Mean reversion** | RSI oversold/overbought + price at Bollinger band | reversion candle |
| S3 | **Momentum breakout** | Close breaks Donchian(N) high/low | **volume > k× volume-SMA** |
| S4 | **Volume Profile** | Reaction at POC / Value-Area edges; LVN breakout | see §6.2 |
| S5 | **MACD** | MACD line × signal line cross; histogram slope | zero-line context |
| S6 | **Supertrend** | ATR-band flip | trend agreement |
| S7 | **VWAP / money flow** | Price vs session VWAP; MFI/CMF | A/D-line slope |
| S8 | **Broker accumulation/distribution** (NEPSE-specific) | Net broker flow + concentration | see §6.3 |
| S9 | **Confluence** | Weighted blend of S1–S8 | thresholded action |

**Ta4j vs custom:** S1, S2, S3, S5, S6, S7 are built from Ta4j `Indicator`s + `Rule`s. **S4 (volume profile)** and **S8 (broker flow)** are custom — they read floorsheet-derived structures Ta4j has no concept of — and are exposed to the confluence layer as ordinary votes so they blend on equal footing. S9 (confluence) is this platform's own scorer. **Pluggable studies (§6.5)** — UT Bot, SMC, Inside Hammer, and any future `CustomIndicator` with `feedsSignalEngine=true` — contribute additional votes automatically without renumbering S1–S8.

---

## 6. Strategies & analytics in depth

### 6.1 Strategy contract & a worked example

Every strategy implements one interface and returns a vote plus structured reasons:

```java
public interface SignalStrategy {
    StrategyId id();
    StrategyVote evaluate(SymbolContext ctx);   // ctx = Ta4j BarSeries + indicators + volume profile + broker flow
}

// vote ∈ [-1, +1]; confidence ∈ [0, 1]; reasons explain the vote
public record StrategyVote(double vote, double confidence, List<Reason> reasons) {}

public record Reason(
    StrategyId strategyId, String indicator, String condition,
    Object observedValue, Object threshold, double contribution, String narrative) {}
```

**Example (S1 trend-following) reason:**
`{indicator:"EMA(9/21)", condition:"EMA9 crossed above EMA21", observedValue:"EMA9=612.4, EMA21=608.9", threshold:"cross", contribution:+0.7, narrative:"Short-term trend turned up: EMA9 crossed above EMA21 with ADX 27 confirming trend strength."}`

**The same S1 over Ta4j** — Ta4j detects the condition; the strategy maps it to a vote + reason (illustrative):

```java
class TrendFollowingStrategy implements SignalStrategy {
    public StrategyVote evaluate(SymbolContext ctx) {
        BarSeries s = ctx.series();
        var close = new ClosePriceIndicator(s);
        var ema9  = new EMAIndicator(close, 9);
        var ema21 = new EMAIndicator(close, 21);
        var adx   = new ADXIndicator(s, 14);
        int i = s.getEndIndex();

        Rule entry = new CrossedUpIndicatorRule(ema9, ema21)
                       .and(new OverIndicatorRule(adx, s.numFactory().numOf(20)));
        Rule exit  = new CrossedDownIndicatorRule(ema9, ema21);

        if (entry.isSatisfied(i)) {
            double conf = confidenceFromGapAndAdx(ema9.getValue(i), ema21.getValue(i), adx.getValue(i));
            return StrategyVote.bullish(conf, new Reason(
                id(), "EMA(9/21)+ADX", "EMA9 crossed above EMA21, ADX>20",
                "EMA9=%s, EMA21=%s, ADX=%s".formatted(ema9.getValue(i), ema21.getValue(i), adx.getValue(i)),
                "cross & ADX>20", +0.7 * conf,
                "Short-term trend turned up: EMA9 crossed above EMA21 with ADX confirming strength."));
        }
        if (exit.isSatisfied(i)) return StrategyVote.bearish(/* symmetric reason */);
        return StrategyVote.neutral();
    }
}
```

The pattern is identical for every Ta4j-based strategy: build the indicators, express the trigger as a `Rule`, then translate the boolean + indicator values into a graded vote and a reason. Custom strategies (S4, S8) skip Ta4j and read the volume-profile / broker-flow structures directly, returning the same `StrategyVote`.

### 6.2 Volume Profile (exact, from floorsheet)

Because you have every transaction, you compute a **true** volume-at-price profile, then the Value Area:

```
INPUT: trades in window (price, qty); binCount B (e.g. 24–50) or tick-aware bin width
1. range = [min(price), max(price)]; binWidth = range / B
2. for each trade: bins[floor((price-min)/binWidth)] += qty
3. POC = index of max(bins)                       # Point of Control
4. Value Area (70% rule):
     vaVol = bins[POC]; lo = hi = POC; total = Σ bins
     while vaVol < 0.70 * total:
         up   = bins[hi+1]  (0 if none)
         down = bins[lo-1]  (0 if none)
         if up >= down: hi++; vaVol += up
         else:          lo--; vaVol += down
     VAH = price_top(hi); VAL = price_bottom(lo)    # Value Area High/Low
5. HVN = local maxima above mean+σ; LVN = local minima (valleys)
```

**Signal uses (S4):**
- Price rejected at **POC** → fade/continuation depending on approach side.
- Close **above VAH** on rising volume → breakout/acceptance (bullish); below **VAL** → bearish.
- **LVN breakout** → fast move expected (thin liquidity); **HVN** → support/resistance shelf.
- **Composite (VPVR)** profile over a multi-week range identifies durable accumulation shelves.

You also offer **time-segmented profiles** (morning vs afternoon) since timestamps are millisecond-precise — useful context, not live signals.

### 6.3 Broker accumulation / distribution (NEPSE-specific edge)

Per (symbol, date), aggregate from buyer/seller IDs:

```
buy[broker]  += qty (and amount) when broker == Buyer
sell[broker] += qty (and amount) when broker == Seller
net[broker]   = buy[broker] - sell[broker]

topSellerShare = max(sell) / Σ sell        # 0..1
topBuyerShare  = max(buy)  / Σ buy
HHI_sell = Σ (sell_i / Σsell)^2            # concentration index
```

**Signal uses (S8):**
- **High seller concentration** (one broker dominating sells, e.g. RSML/broker-28 in your sample) on a down/flat day → distribution → bearish tilt.
- **High buyer concentration** with rising price → accumulation → bullish tilt.
- **Persistent net-buying broker over a rolling window** → smart-money accumulation flag.
- Surface "top accumulating / distributing brokers" per symbol as both an analytic and an alert.

> Caveat to encode in narratives: a single broker often *aggregates many clients*, so concentration is **suggestive, not conclusive** — the engine weights it as one input among many, never as a standalone trigger.

### 6.4 Confluence scoring (final BUY/SELL/HOLD)

```
score = 100 * Σ_i ( w_i * vote_i * confidence_i ) / Σ_i ( w_i )     # ∈ [-100, +100]
action = BUY  if score >=  +T_buy   (e.g. +35)
         SELL if score <=  -T_sell  (e.g. -35)
         HOLD otherwise
```

- Weights `w_i` per strategy are config-driven and **tunable via backtests**.
- The signal stores the full vote vector, so the UI can show the **breakdown** ("4 of 6 trend/volume strategies bullish; broker flow neutral; mean-reversion dissenting") and the **top reasons**.
- Optional regime gate: in a strong down-market, raise `T_buy` to avoid catching falling knives — the concrete input is **EMA/ADX computed on the `NEPSE` aggregate/index series** (F3) plus market breadth.
- **Backtesting hook:** the score is also exposed as a custom Ta4j `Indicator<Num>` (returning `score(i)` at each index), so the whole confluence model can be backtested as a Ta4j strategy: `entry = OverIndicatorRule(scoreIndicator, +T_buy)`, `exit = UnderIndicatorRule(scoreIndicator, −T_sell)`. This is what lets §7 reuse Ta4j's backtester for the confluence signal, not just the individual strategies.

---

### 6.5 Custom indicator framework (extensible studies)

"Indicator" is too narrow for what charting needs: **HMA** is a line, **UT Bot** is a line *plus* buy/sell events, **Inside Hammer** is a discrete *pattern marker*, and **SMC** is a set of *zones* (boxes/lines), not a series at all. So studies are modeled by **output kind**, and new ones plug in without touching the engine.

**Output kinds.** `LINE` (single series — HMA), `BAND` (multi-line — Bollinger/Keltner), `SIGNAL` (a plotted line + discrete buy/sell events — UT Bot, Supertrend), `MARKER` (discrete pattern events — Inside Hammer, engulfing), `ZONE` (geometric regions: boxes/rays/labels — SMC). The charting layer (§F7) dispatches on this: `LINE`/`BAND` → `addSeries`/pane; `SIGNAL`/`MARKER` → `createSeriesMarkers` (+ a line for `SIGNAL`); `ZONE` → custom box/line primitives.

**The plugin SPI.** Each study implements one interface and is registered as a Spring bean; a registry discovers all of them at startup and serves the catalog. Adding a study later = drop in a new `@Component` — no engine, API, or UI changes.

```java
public interface CustomIndicator {
    IndicatorDescriptor descriptor();              // id, name, category, param schema, outputKind, feedsSignalEngine
    IndicatorResult     compute(BarSeries s, ParamValues p);
}

public enum OutputKind { LINE, BAND, SIGNAL, MARKER, ZONE }

public record ParamSpec(String name, ParamType type, Object def, Object min, Object max, List<String> options) {}

public sealed interface IndicatorResult permits Lines, Signals, Markers, Zones {}
//   Lines(Map<String,List<Point>>)                              — HMA, bands
//   Signals(List<Point> plot, List<Event> buySell)              — UT Bot
//   Markers(List<MarkerEvent> markers)                          — Inside Hammer
//   Zones(List<Box> boxes, List<Ray> lines, List<Label> labels) — SMC
```

The descriptor's **param schema** is what makes the frontend generic: `/indicators/catalog` returns it, the "Add indicator" dialog builds the settings form from it, and `/indicators/compute` returns the typed result. Numeric studies (`LINE`/`BAND`/`SIGNAL`) also expose a Ta4j `Indicator<Num>` view, so they feed the signal engine and **backtest** through the existing Ta4j path (§7); pattern/zone studies feed the signal engine as boolean/event votes (like S4/S8). A study whose descriptor sets `feedsSignalEngine=true` automatically contributes a vote to confluence (§6.4).

**The four requested studies:**
- **HMA — Hull Moving Average** (`LINE`). `HMA(n) = WMA(2·WMA(n/2) − WMA(n), round(√n))`, composed from Ta4j `WMAIndicator`. Param: `period` (default 21). Optional vote: price vs HMA + HMA slope.
- **UT Bot** (`SIGNAL`). An ATR trailing stop — recursive (like Supertrend), so a Ta4j `RecursiveCachedIndicator<Num>`: `nLoss = a · ATR(c)`; the stop ratchets with price and flips side on a close-through; **buy** when close crosses above the stop, **sell** when below. Params: `keyValue a` (1.0), `atrPeriod c` (10), optional EMA confirmation, optional Heikin-Ashi source. Output: the stop line + buy/sell events — both a chart overlay and a strong directional vote.
- **Inside Hammer** (`MARKER`). Inside bar (`high<prevHigh ∧ low>prevLow`) that is *also* a hammer (small real body in the upper third, lower shadow ≥ 2× body, minimal upper shadow) — composed from Ta4j `RealBodyIndicator`/`UpperShadowIndicator`/`LowerShadowIndicator` plus the inside-bar check (Ta4j has no dedicated hammer). Params: `bodyMaxPct`, `shadowRatio`, `requirePriorDowntrend`. Output: a marker at each qualifying bar + a bullish-reversal vote (weighted higher in a discount zone / after a bullish CHoCH).
- **SMC — Smart Money Concepts** (`ZONE`). A structural toolkit on fractal **swing detection** (lookback `k`): **market structure** (HH/HL/LH/LL, **BOS**, **CHoCH**), **order blocks** (last opposing candle before a structure-breaking impulse), **fair-value gaps** (3-bar imbalance), **liquidity** (equal highs/lows + sweeps), and **premium/discount** halves of the dealing range. Params: `swingLookback` (5), `fvgMinPct`, `equalLevelTolerancePct`, `maxZones`, mitigation rule. Output: boxes (OB/FVG), rays (liquidity/structure), labels (BOS/CHoCH) as custom primitives, plus a composite vote (e.g. bullish OB tap in discount after a bullish CHoCH).

**Levels of "custom" (security in Decision E):** Ta4j-backed built-ins and the developer-authored plugins above (compiled, reviewed) are the default; config-composed studies (existing building blocks combined via validated JSON, e.g. a generic "MA cross" template) come next; user-scripted studies are gated behind a strict sandbox and off by default.

---

### 6.6 Screener metrics (relative volume & sharp-drop detection)

These power the dashboard (F11) and the price-drop screener (F12). All windows are **trading-day** counts from the calendar, and a **liquidity floor** (min turnover/volume) is applied so thin scrips don't dominate the rankings.

**Relative Volume (RVOL).** `RVOL = volume_today / SMA(volume, N)` over the prior `N` trading days (default `N=20`, excluding today), reusing the volume-SMA indicator. **Spike** when `RVOL ≥ s` (default 2–3×); **drop** when `RVOL ≤ d` (default ≤ 0.3–0.5×). The same ratio on **turnover** gives value-weighted activity.

**Volume z-score.** `z = (volume_today − meanₙ) / stddevₙ`. More robust than the raw ratio for *ranking* unusual activity — a 2× ratio on a noisy low-volume name is far less significant than `z ≥ 3`. The dashboard **ranks spikes by z-score** but **labels them with the ratio** (which traders read intuitively).

**High / Low trade ranking.** Rank by `turnover`, `volume`, or `trades_count` — surfaced together because they diverge (a pricey scrip can top turnover with modest volume). "Low trade" is the liquidity tail, with the floor applied to avoid zero-trade noise.

**Sharp price drop** (three lenses; "sharp" means *speed*, not just size):
- *Point-to-point* — `%Δ = (close_t − close_{t−N}) / close_{t−N}` (the default).
- *Drawdown-from-window-high* — `dd = (close_t − max(high)_{t−N..t}) / max(high)_{t−N..t}` — catches run-up-then-collapse that point-to-point misses.
- *Sharpness (velocity)* — magnitude normalized by volatility, e.g. `%Δ / (ATR%·√N)`, or the largest single down-stretch / count of consecutive down days — separates a fast crash from a slow grind.

Each result row is annotated with **RVOL context** (did the move happen on heavy or light volume?) and the scrip's **current signal**, so the same numbers serve both "what's unusual today" and "what's been falling, and does the engine agree."

---

## 7. Backtesting methodology

### 7.1 Bias control
- **Signal-at-close, fill-next-open** is the default: a signal computed from day *T*'s close is executed at day *T+1*'s open. No indicator may read data after its own bar.
- Survivorship: include delisted/suspended symbols for the period they traded (reference module tracks status).
- Walk-forward option: optimize weights on a training window, validate out-of-sample.

### 7.2 NEPSE cost model (fully configurable)
Model the real frictions; **set exact rates from current SEBON/NEPSE circulars** (they change — keep them in config, not code):
- **Broker commission** — tiered by transaction value (smaller trades pay a higher %).
- **SEBON regulatory fee** — small % of turnover.
- **DP charge** — flat fee per scrip per settlement on the sell side.
- **Capital Gains Tax** — rate depends on holding period and entity type; applied on realized gains.
- **Slippage** — configurable (bps on fill price, or fraction of tick), since EOD fills are assumptions.

### 7.3 Metrics
Total return, CAGR, annualized volatility, **Sharpe**, **Sortino**, **max drawdown** & duration, **win rate**, **profit factor**, average win/loss, expectancy, exposure %, trade count. The standard ones come straight from Ta4j `AnalysisCriterion`s (return, drawdown, win ratio, profit factor, position counts); the rest (Sortino, CAGR, exposure, and any cost-adjusted variant) are implemented as **custom `AnalysisCriterion`s** against the Ta4j `TradingRecord` — the interface is built for exactly this. Outputs: summary metrics (JSONB) + equity curve + drawdown curve + per-trade log (entry/exit dates, prices, qty, costs, P&L, **entry/exit reasons**).

---

## 8. Data model (key tables)

```sql
-- reference
instrument(symbol PK, name, sector, type, listed_shares, status, price_band, created_at)
  -- type ∈ {EQUITY, INDEX}; reserved symbol 'NEPSE' is the whole-market aggregate/index.
broker(broker_id PK, name, status)
trading_day(trade_date PK, is_open, note)

-- ingestion / raw  (PARTITION BY RANGE (trade_date), monthly)
floorsheet_trade(
  contract_id PK, symbol, buyer_broker, seller_broker,
  quantity, price NUMERIC(18,4), amount NUMERIC(20,4),
  trade_time TIMESTAMP, trade_date DATE, source_file_id, ingested_at,
  INDEX(symbol, trade_date))
ingestion_batch(id PK, file_count, date_from, date_to, status,
  submitted_by, submitted_at, finished_at)
ingestion_job(id PK, batch_id FK NULL, trade_date, source_filename, file_hash,
  rows_read, rows_accepted, rows_rejected, rows_duplicate, status,
  started_at, finished_at, INDEX(trade_date, started_at))
  -- multiple rows per date are allowed (reprocessing history); row-level idempotency
  -- is enforced by the floorsheet_trade.contract_id upsert, not here.
ingestion_rejection(id PK, job_id FK, raw_line, reason_code, detail)

-- market data
daily_candle(symbol, trade_date, open, high, low, close, volume, turnover,
  trades_count, vwap, prev_close, change_pct, PRIMARY KEY(symbol, trade_date))
market_aggregate_daily(trade_date PK, total_volume, total_turnover, total_trades,
  advances, declines, unchanged,
  index_proxy_open, index_proxy_high, index_proxy_low, index_proxy_close,  -- nullable: cap-weighted proxy
  official_index_close)                                                    -- nullable: ingested official index
  -- backs the 'NEPSE' symbol; daily_candle row for 'NEPSE' (if used by charting) mirrors these.
intraday_candle(symbol, trade_date, interval, bucket_start, o,h,l,c,v,
  PRIMARY KEY(symbol, trade_date, interval, bucket_start))   -- partitioned monthly
volume_profile(symbol, window_from, window_to, poc, vah, val,
  bins JSONB, PRIMARY KEY(symbol, window_from, window_to))
broker_flow_daily(symbol, trade_date, broker_id, buy_qty, sell_qty, net_qty,
  buy_amount, sell_amount, PRIMARY KEY(symbol, trade_date, broker_id))

-- indicators / signals
indicator_snapshot(symbol, trade_date, values JSONB,
  rsi14, ema9, ema21, atr14,           -- promoted columns for fast filtering
  PRIMARY KEY(symbol, trade_date))
strategy_config(id PK, name, type, params JSONB, weight, enabled)
signal(id PK, symbol, trade_date, action, score, confidence,
  reasons JSONB, votes JSONB, indicator_snapshot_ref, generated_at,
  INDEX(trade_date, action), INDEX(symbol, trade_date))

-- backtests
backtest_run(id PK, strategy_config_id, symbols, date_from, date_to,
  starting_capital, cost_model JSONB, status, created_by, created_at)
backtest_result(run_id PK FK, metrics JSONB, equity_curve_ref, created_at)
backtest_trade(id PK, run_id FK, symbol, entry_date, entry_price,
  exit_date, exit_price, qty, costs, pnl, return_pct, entry_reason, exit_reason)

-- users / watchlists / notifications
app_user(id PK, email UNIQUE, password_hash, role, created_at)
watchlist(id PK, user_id FK, name)
watchlist_item(watchlist_id FK, symbol, PRIMARY KEY(watchlist_id, symbol))
alert_rule(id PK, user_id FK, type, params JSONB, enabled)   -- incl. RVOL_SPIKE / RVOL_DROP / PRICE_DROP
saved_screen(id PK, user_id FK, name, definition JSONB, created_at)
  -- dashboard panels and the 30/45/60 price-drop presets are Redis-cached per date (no RDBMS table needed);
  -- only user-saved custom screens are persisted here.
notification(id PK, user_id FK, signal_id, title, body, read, sent, created_at)

-- spring batch metadata (BATCH_JOB_INSTANCE / BATCH_JOB_EXECUTION / BATCH_STEP_EXECUTION ...)
-- is created and owned by Spring Batch; it provides run history + restart-from-failed-step.
-- notification dispatch uses the notification.sent flag above as a lightweight outbox.
```

---

## 9. API catalog (all features)

REST under `/api`, versioned with Spring Boot 4's **native API versioning** (header or path). All list endpoints are paged (`page`, `size`, `sort`). Dates are NPT trading dates. Admin/analyst endpoints are role-gated.

### Auth / IAM
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/auth/register` | Create account |
| POST | `/api/v1/auth/login` | Obtain access + refresh tokens |
| POST | `/api/v1/auth/refresh` | Rotate access token |
| POST | `/api/v1/auth/logout` | Invalidate refresh token |
| GET | `/api/v1/users/me` | Current profile |
| PATCH | `/api/v1/users/me` | Update profile/preferences |

### Reference & Calendar
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/instruments` | List/filter scrips (sector, status, q) |
| GET | `/api/v1/instruments/{symbol}` | Scrip detail |
| GET | `/api/v1/sectors` | Sector list |
| GET | `/api/v1/brokers` | Broker registry |
| GET | `/api/v1/brokers/{id}` | Broker detail |
| GET | `/api/v1/calendar/trading-days` | Trading days in range |
| GET | `/api/v1/calendar/is-open` | Is a given date a trading day |

### Ingestion (admin)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/ingestion/uploads` | Upload a single `YYYY-MM-DD.csv` (date taken from filename) |
| POST | `/api/v1/ingestion/uploads/batch` | **Upload many `YYYY-MM-DD.csv` files** (multipart); validates filenames, archives, enqueues an ordered backfill; returns `202` + `batchId` + per-file intake status |
| POST | `/api/v1/ingestion/trigger` | Trigger pipeline for `date` (or `from`/`to` range) |
| POST | `/api/v1/ingestion/webhook` | Scraper completion callback (signed) |
| GET | `/api/v1/ingestion/batches` | List batch jobs (status, file count, progress) |
| GET | `/api/v1/ingestion/batches/{batchId}` | Batch status + per-date progress (queued/ingested/aggregated/signaled/failed) |
| GET | `/api/v1/ingestion/batches/{batchId}/files` | Per-file/per-date results (accepted/rejected/duplicate, row counts) |
| POST | `/api/v1/ingestion/batches/{batchId}/retry` | Retry only the failed dates in the batch |
| GET | `/api/v1/ingestion/jobs` | List per-file ingestion jobs |
| GET | `/api/v1/ingestion/jobs/{id}` | Job status + counts |
| GET | `/api/v1/ingestion/jobs/{id}/rejections` | Rejected rows + reasons |
| POST | `/api/v1/ingestion/reprocess` | Reprocess a date (or range) from archived raw files |

### Market Data
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/market/trades` | Floorsheet by `symbol`/`date` (paged) |
| GET | `/api/v1/market/candles` | Daily OHLCV (`symbol`, `from`, `to`) |
| GET | `/api/v1/market/candles/intraday` | Intraday candles (`symbol`, `date`, `interval`) |
| GET | `/api/v1/market/vwap` | Session/anchored VWAP |
| GET | `/api/v1/market/volume-profile` | POC/VAH/VAL + bins (`symbol`, `from`, `to`, `bins`) |
| GET | `/api/v1/market/broker-flow` | Per-broker net flow for `symbol`/`date` |
| GET | `/api/v1/market/broker-flow/top` | Top accumulating/distributing brokers |
| GET | `/api/v1/market/movers` | Gainers/losers/volume/turnover for `date` |
| GET | `/api/v1/market/summary` | Breadth, advances/declines, sector summary |
| GET | `/api/v1/market/aggregate` | Whole-market `NEPSE` series: totals + breadth + index proxy/official; `?from=&to=` (the same data charts via `/charts/NEPSE`) |

### Indicators
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/indicators/catalog` | All studies (built-in + custom) with **param schema + output kind** — drives the "Add indicator" dialog |
| GET | `/api/v1/indicators` | Series for `symbol`/`name`/`params`/range |
| GET | `/api/v1/indicators/{symbol}/latest` | Latest indicator snapshot |
| POST | `/api/v1/indicators/compute` | Ad-hoc compute (custom params); returns the **typed result by output kind** (lines / signals / markers / zones) — works for custom studies (HMA, UT Bot, Inside Hammer, SMC) too |
| GET | `/api/v1/indicators/custom` | List registered `CustomIndicator` plugins (enabled state, defaults) |
| PATCH | `/api/v1/indicators/custom/{id}` | *(admin)* Enable/disable a study, set default params |
| POST | `/api/v1/indicators/custom/definitions` | *(admin)* Register a **config-composed** study (validated JSON only — never arbitrary code; see Decision E) |

### Signals & Strategies
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/signals/latest` | Latest run's lists (`action=BUY/SELL/HOLD`) |
| GET | `/api/v1/signals` | Filter by `date`/`action`/`sector`/`minScore` |
| GET | `/api/v1/signals/{id}` | Signal detail: score, votes, full reasons |
| GET | `/api/v1/signals/symbol/{symbol}` | Signal history for a scrip |
| POST | `/api/v1/signals/generate` | (admin) Re-run signal generation for `date` |
| GET | `/api/v1/strategies` | List strategies/configs |
| GET | `/api/v1/strategies/{id}` | Strategy detail |
| POST | `/api/v1/strategies` | (analyst) Create custom config |
| PATCH | `/api/v1/strategies/{id}` | (analyst) Update params/weight/enabled |

### Backtesting
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/backtests` | Run a backtest (strategy, symbols, range, capital, cost model) |
| GET | `/api/v1/backtests` | List runs |
| GET | `/api/v1/backtests/{id}` | Status + summary |
| GET | `/api/v1/backtests/{id}/metrics` | Full metrics |
| GET | `/api/v1/backtests/{id}/equity-curve` | Equity & drawdown series |
| GET | `/api/v1/backtests/{id}/trades` | Trade log with reasons |
| POST | `/api/v1/backtests/compare` | Compare multiple runs |
| DELETE | `/api/v1/backtests/{id}` | Delete a run |

### Charting
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/charts/{symbol}` | Composite payload for **Lightweight Charts**: candles + `indicators=` overlays + `overlays=volprofile` + signal markers (arrays map 1:1 to LWC series) |
| GET | `/api/v1/charts/{symbol}/markers` | Signal markers only (`createSeriesMarkers` shape) — for refreshing markers without re-fetching candles |
| GET | `/api/v1/charts/udf/config` | *(Advanced Charts only)* Datafeed config (`onReady`) |
| GET | `/api/v1/charts/udf/symbols` | *(Advanced Charts only)* `resolveSymbol` metadata |
| GET | `/api/v1/charts/udf/search` | *(Advanced Charts only)* `searchSymbols` |
| GET | `/api/v1/charts/udf/history` | *(Advanced Charts only)* `getBars` history (UDF `t/o/h/l/c/v` arrays) |

### Screeners & Dashboard
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/dashboard/day` | Composite **day transaction analysis** (F11): breadth + high/low-trade panels + RVOL spikes/drops; `?date=&window=20` |
| GET | `/api/v1/screener/active` | High/low trade: `?date=&by=turnover\|volume\|trades&order=high\|low&limit=` |
| GET | `/api/v1/screener/relative-volume` | RVOL scan: `?date=&window=20&type=spike\|drop&minRatio=&minZScore=&page=` |
| GET | `/api/v1/screener/price-drop` | **Sharp price-drop** (F12): `?window=30&metric=pctchange\|drawdown\|sharpness&threshold=&sector=&minTurnover=&page=` (presets 30/45/60 cached; custom on demand) |
| POST | `/api/v1/screener/scan` | Run an ad-hoc / saved scan definition (generic, forward-looking) |
| GET | `/api/v1/screener/definitions` | List the user's saved screens |
| POST | `/api/v1/screener/definitions` | Save a custom screen |

Screener-backed **alert rule types** (via `/alerts`, §F9): `RVOL_SPIKE`, `RVOL_DROP`, `PRICE_DROP` — e.g. "notify when any watchlist scrip's 30-day drop ≤ −20%" or "RVOL ≥ 3 on SYMBOL".

### Watchlist / Portfolio
| Method | Path | Purpose |
|---|---|---|
| GET / POST | `/api/v1/watchlists` | List / create |
| GET / PUT / DELETE | `/api/v1/watchlists/{id}` | Read / rename / delete |
| POST | `/api/v1/watchlists/{id}/items` | Add symbol |
| DELETE | `/api/v1/watchlists/{id}/items/{symbol}` | Remove symbol |

### Notifications & Alerts
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/notifications` | Paged feed |
| PATCH | `/api/v1/notifications/{id}/read` | Mark read |
| POST | `/api/v1/notifications/read-all` | Mark all read |
| GET | `/api/v1/notifications/stream` | **SSE** fallback stream |
| GET / POST | `/api/v1/alerts` | List / create alert rule |
| PATCH / DELETE | `/api/v1/alerts/{id}` | Update / delete rule |

### System / Realtime
| Method | Path | Purpose |
|---|---|---|
| GET | `/actuator/health`, `/actuator/metrics` | Health + metrics |
| GET | `/api/v1/system/pipeline/status` | Last run state per stage |
| WS | `/ws` (STOMP) | Topics: `/topic/signals`, `/user/queue/notifications`, `/topic/pipeline-status` |

---

## 10. Backend implementation steps (build order)

### 10.1 Step 1 — Project & platform skeleton
- Spring Boot 4.0.x, Java 25 toolchain; enable virtual threads.
- Add `spring-boot-starter-batch` (pipeline orchestration), `org.ta4j:ta4j-core` (latest 0.22.x, ≥ 0.22.7 — verify on Java 25 in CI; see version anchors), and `archunit-junit5` (test scope, boundary rules); Actuator + Micrometer for observability.
- Postgres + Flyway (single migration set, organized into per-module folders by naming convention), Redis, object-store client.
- `platform` module: error model (`@RestControllerAdvice`), pagination, NPT clock, calendar utilities, OpenAPI (springdoc).
- Write **ArchUnit boundary tests** (e.g. "no module may depend on another module's `internal` package") so boundary violations fail the build.

### 10.2 Step 2 — Reference & calendar
- Entities + repositories for instrument/broker/`trading_day`; seed loaders.
- Calendar service powering trading-day look-backs (used by every downstream module).

### 10.3 Step 3 — Ingestion (the careful part)
- **Filename contract:** validate against `^\d{4}-\d{2}-\d{2}\.csv$`, parse the date, and **never reuse the raw filename as a path** — store under a controlled key like `raw/{date}/{contentHash}.csv` (path-traversal safe).
- **Batch upload endpoint:** accept multipart files at `/ingestion/uploads/batch`; enforce per-batch caps (max file count, max total bytes); reject malformed names and duplicate dates *within the batch*; create `ingestion_batch` + one `ingestion_job` per file; return `202` immediately and process asynchronously. Sort files **ascending by date** before processing.
- **Streaming parser** (univocity/Commons CSV) — never load the whole file into memory.
- **Timestamp parsing:** ordered `DateTimeFormatter` list, e.g. `H:mm:ss:SSS a` after normalizing the comma-after-date variant; reject unparseable rows to quarantine. **Cross-check** each row's timestamp date against the filename date; mismatch → quarantine.
- **Validation:** positive qty/price; `|amount − qty*price| ≤ tolerance`; symbol known-or-provisional; broker IDs numeric.
- **Dedup:** upsert keyed on `contract_id` (idempotent re-runs and overlapping files).
- **Spring Batch jobs:** ingestion is a `MultiResourceItemReader` (or a partitioned step) over the batch's files for the parallel ingest phase; the downstream **indicator/signal phases iterate dates ascending** (parallel across symbols, sequential across dates). Skip policy routes bad rows to `ingestion_rejection`; the job repository makes the batch restartable from the failed date.
- **Backfill notifications:** pass a `suppressNotifications` flag for historical dates so only the latest date (or the live daily run) fans out to users.
- Archive raw CSV (content hash) before parsing; record per-file `ingestion_job` and roll status up to `ingestion_batch`.
- Publish `TradesIngestedEvent` per processed date.

### 10.4 Step 4 — Market data aggregation
- Daily candle builder (time-ordered O/H/L/C, volume, turnover, VWAP, prev_close, change%).
- Volume-profile builder (§6.2) and broker-flow builder (§6.3); optional intraday bucketing.
- **Market aggregate (`NEPSE`):** sum the day across all symbols into `market_aggregate_daily` (total turnover/volume/trades + advances/declines/unchanged) and a market-wide broker flow; if `listed_shares` is populated, also compute the **cap-weighted index proxy** (labeled approximate); leave a hook to ingest the **official index** from a separate feed. Skip volume profile for `NEPSE`.
- Movers/summary queries. Publish `MarketDataReadyEvent`.

### 10.5 Step 5 — Indicator engine
- Build a `candles → Ta4j BarSeries` adapter (`BaseBarSeriesBuilder`), choosing `DecimalNum` for the canonical path.
- `IndicatorService` resolves the catalog through Ta4j `Indicator<Num>` (RSI/EMA/MACD/Bollinger/ATR/ADX/Supertrend/OBV/MFI/Pivots come from the library); keep **volume profile** and **broker flow** as custom builders in `marketdata`.
- **Custom-study SPI (§6.5):** define `CustomIndicator` (+ `IndicatorDescriptor`, `OutputKind`, sealed `IndicatorResult`); a `CustomIndicatorRegistry` auto-discovers all `@Component` beans and feeds `/indicators/catalog`.
- Implement the shipped studies: **HMA** (`LINE`, via `WMAIndicator`), **UT Bot** (`SIGNAL`, recursive ATR stop as `RecursiveCachedIndicator`), **Inside Hammer** (`MARKER`, via real-body/shadow indicators + inside-bar check), **SMC** (`ZONE`: swing detection → BOS/CHoCH, order blocks, FVG, liquidity, premium/discount). Expose numeric ones as Ta4j `Indicator<Num>` so they backtest; set `feedsSignalEngine` to wire votes into confluence.
- Persist the canonical per-(symbol, date) snapshot; cache parametrized series in Redis. Publish `IndicatorsComputedEvent`.

### 10.6 Step 6 — Signal engine
- `SignalStrategy` interface; implement S1, S2, S3, S5, S6, S7 with Ta4j `Indicator`s + `Rule`s, and S4/S8 as custom (volume profile / broker flow). Each maps its trigger to a graded `StrategyVote`.
- Confluence scorer (S9) with config-driven weights; expose the score as a custom Ta4j `Indicator` for backtesting.
- Structured `Reason` model + narrative templating.
- Persist `signal` (action, score, votes, reasons). Publish `SignalsGeneratedEvent`.

### 10.7 Step 7 — Backtesting
- Drive per-symbol runs with Ta4j `BarSeriesManager` / `BacktestExecutor` → `TradingRecord`; use the confluence-score indicator + `Over/UnderIndicatorRule` for confluence backtests; shift fills to next-open to avoid look-ahead.
- Custom layer: NEPSE cost model (tiered brokerage, SEBON fee, per-scrip DP, CGT, slippage) and shared-capital portfolio aggregation across symbols.
- Metrics from Ta4j `AnalysisCriterion`s + custom criteria (Sortino, CAGR, exposure, cost-adjusted return); equity/drawdown curves; trade log with reasons; run versioning + compare.

### 10.8 Step 8 — Screener & dashboard
- New `screener` module composing marketdata + indicator + signal (read-only; ArchUnit allows it to depend on their published APIs).
- Implement **RVOL** (ratio + z-score) from the volume-SMA indicator + windowed stddev; **activity rankings** (turnover/volume/trades) from `daily_candle`; **price-drop** metrics (%Δ, drawdown-from-high, ATR-normalized sharpness) from candles + ATR (§6.6).
- Trading-day windowing via the calendar; liquidity floor and warm-up guard applied to every scan.
- Pre-warm the day dashboard and the 30/45/60 drop lists on `SignalsGeneratedEvent`; cache in Redis; compute custom windows on demand.
- Endpoints under `/dashboard` and `/screener`; annotate each row with the current signal + chart link; register `RVOL_SPIKE`/`RVOL_DROP`/`PRICE_DROP` alert-rule types.

### 10.9 Step 9 — Charting API
- Composite `/charts/{symbol}` endpoint shaping the payload to Lightweight Charts series (candles, line overlays, volume histogram, profile bins, markers); ETag/caching.
- Markers in `createSeriesMarkers` shape; profile bins with POC/VAH/VAL for the custom primitive. Since data is EOD, the chart refreshes once per day — push the new bar/markers over the existing WebSocket rather than polling intraday.

### 10.10 Step 10 — Notifications & realtime
- Alert-rule evaluator on `SignalsGeneratedEvent`; persist notifications; STOMP WebSocket + SSE fallback; Redis backplane if multi-instance.

### 10.11 Step 11 — IAM, watchlists, alerts
- Spring Security 6 + JWT (access/refresh), Argon2id hashing, role gating; watchlist & alert CRUD.

### 10.12 Step 12 — Orchestration & scheduling
- `@Scheduled` 15:01 NPT trigger (+ ShedLock if replicated) launches the Spring Batch job; admin manual trigger + reprocess; pipeline-status endpoint reads Batch job/step execution state; restartability comes from the Batch JobRepository.

### 10.13 Step 13 — Observability, caching, testing
- Micrometer metrics + tracing; structured logging with a correlation/trace id per pipeline run.
- Redis caching with explicit eviction on recompute.
- Tests: ArchUnit boundary tests, per-module `@SpringBootTest` slices, Testcontainers (Postgres/Redis), `@SpringBatchTest` for the pipeline job (including restart-from-failed-step), golden-file ingestion tests (including your messy sample rows), and **deterministic backtest regression tests**.

---

## 11. Cross-cutting: security defaults & decisions

Per the principle of secure-by-default, here are the choices with the safest option flagged:

**Decision A — Treat the scraped CSV as hostile input (no exceptions).**
- *Safest default (chosen):* stream-parse with strict validation, quarantine rejects, bound file size/row count, neutralize formula-injection on any future export, never `eval`/reflectively execute cell content.
- *Tradeoff:* slightly more code than a naive `String.split`, but it prevents memory-exhaustion and injection from a compromised upstream page.

**Decision B — Token storage (web client).**
1. **httpOnly, Secure, SameSite cookies for the refresh token + short-lived in-memory access token** — *safest default*: refresh token unreachable by JS, mitigates XSS token theft; pair with CSRF protection.
2. Access + refresh both in `localStorage` — simplest, but XSS-exfiltratable. **Not recommended.**
3. BFF/session cookie with server-side session — strongest, more infrastructure.
- *Proceeding with option 1* unless you explicitly want the BFF pattern.

**Decision C — Admin pipeline triggers & scraper webhook.**
- *Safest default (chosen):* role `ADMIN` for trigger/reprocess; webhook authenticated with an HMAC signature + timestamp (replay window), not just a shared secret in a query param.
- *Tradeoff:* signing adds a step on the scraper side; worth it to stop forged ingestion.

**Decision D — Batch upload input handling.**
1. **Multipart of individual `YYYY-MM-DD.csv` files with strict caps** — *safest default*: bounded file count and total size per request, filename validated against the date regex and never used as a path, each streamed and parsed; rejects don't fail the batch.
2. Accept a single `.zip` of daily files — convenient for large historical backfills, but adds **zip-bomb / path-traversal** risk. Only acceptable with hard decompression limits (max entries, max decompressed size, no nested archives, sanitized entry names) — *not the default*.
3. Pull from a trusted object-store prefix the scraper writes to (admin supplies a key range) — strongest for big backfills since nothing transits the API, but needs the storage integration wired first.
- *Proceeding with option 1*; offer option 3 for multi-year seeding when the object store is in place.

**Decision E — Adding custom indicators (extensibility vs code execution).**
1. **Developer-authored Java plugins + config-composed studies** — *safest default*: new studies are `@Component`s reviewed in code (§6.5), or composed from existing building blocks via **validated JSON** templates (e.g. "MA cross" with bounded params). No untrusted code runs; the param schema is allow-listed and range-checked.
2. **Sandboxed expression DSL for power users** — a side-effect-free expression language over the bar series, with **no I/O, no reflection, no class loading**, a whitelisted function set, and hard CPU-time / iteration / memory caps evaluated off the request thread. Acceptable *only* fully sandboxed; off by default.
3. **Arbitrary Groovy/JS/Java `eval`** — full ambient authority, RCE risk. **Rejected.**
- *Proceeding with option 1*; option 2 only behind the sandbox above if user-scripting is ever required. This is why `/indicators/custom/definitions` accepts configuration, never code.

**Always-on baselines:** HTTPS only; CORS locked to the Angular origin; per-user + per-IP rate limiting (Redis) on auth and heavy endpoints; secrets from environment/secret manager (never in code, configs, logs, or tests); least-privilege DB role; validate and bound every query parameter (date ranges, `bins`, page size). Spring Boot 4 removed legacy OAuth2 auto-config — use **Spring Authorization Server** if/when you add OAuth2/OIDC.

---

## 12. Frontend integration notes (Angular 22)

Backend-focused, but to align with your Angular 22 choice:
- **Signal-first:** Angular 22 makes `OnPush` the default and ships **stable Signal Forms** and the **Resource API** (`resource`, `rxResource`, `httpResource`) — use `httpResource`/`rxResource` to bind chart, signal-list, and backtest data reactively; HttpClient now uses **Fetch** by default.
- **Realtime:** subscribe to the STOMP `/topic/signals` and `/user/queue/notifications`; render toasts + a notification center; SSE fallback via the stream endpoint.
- **Charting:** **TradingView Lightweight Charts™ v5** fed by the `/charts/{symbol}` composite payload. Price as a `CandlestickSeries`; indicators as overlays or in their own **panes** (v5 multi-pane) for RSI/MACD/volume; BUY/SELL markers via the v5 **`createSeriesMarkers(series, …)`** plugin (note: not v4's `series.setMarkers()`); **volume profile as a custom series primitive** (right-aligned horizontal histogram with POC/VAH/VAL guides). Clicking a marker opens the reasons panel. Enable the `attributionLogo` layout option to meet the Apache-2.0 attribution requirement. EOD data means the chart updates once daily — apply the new bar + markers from the WebSocket push, no intraday polling.
- **Reasons UX:** the signal detail view renders the structured `reasons[]` (per-strategy contribution bars + narrative) so every BUY/HOLD/SELL is explained, not asserted.
- **Dashboard & screeners:** a **Day Transaction Analysis** page (F11) of tiles — high/low trade by turnover/volume/trades, relative-volume spikes and drops, market breadth — and a **Sharp Price-Drop** page (F12) with a window selector (30 / 45 / 60 / custom) plus sector and liquidity filters. Both bind to the `/dashboard` and `/screener` endpoints via `httpResource`; every row deep-links to the TradingView chart and shows the scrip's current signal, and a spike/drop row can be turned into an alert in one click.
- Requires **TypeScript 6** and **Node 26**; `@Service` decorator + `injectAsync` help lazy-load heavy chart/backtest modules.

---

### Version anchors (verified, mid-2026)
- **Spring Boot 4.0** — GA 2025-11-30; Spring Framework 7, Jakarta EE 11; Java 17 baseline, **first-class Java 25**; native API versioning + HTTP Service Clients; Micrometer observability; Spring Authorization Server (legacy OAuth2 auto-config removed); Undertow removed.
- **Spring Batch** — bundled via `spring-boot-starter-batch` on the Boot 4 BOM; JobRepository provides restart-from-failed-step and run history. **ArchUnit** (test scope) enforces module boundaries at build time.
- **Ta4j-core 0.22.x** (≥ 0.22.7) — pure Java (deps: slf4j, commons-math3, gson); 130+ indicators incl. candlestick patterns, composable `Rule`/`Strategy`, `BarSeriesManager`/`BacktestExecutor` with `AnalysisCriterion`s (incl. `SortinoRatioCriterion`), `DecimalNum`/`DoubleNum`, thread-safe `CachedIndicator`s, native multithreaded backtesting. **Java 25 caveat:** the bytecode targets Java 8 (so it *loads* on 25), but the maintainers state Ta4j does **not yet officially support Java 25** (some Java-25 test fixes have landed; full support is slated for a later release) — pin the version, run a smoke/regression test of the indicators you use on Java 25 in CI, and track the release that declares official support.
- **Angular 22** — released 2026-06-03; stable Signal Forms / Resource API / Aria; `OnPush` default; HttpClient Fetch default; TypeScript 6, Node 26; strong SSRF/sanitization hardening.
- **TradingView Lightweight Charts™ v5** (docs at v5.2) — Apache-2.0, ~35 kB; **attribution required** (`attributionLogo` option or NOTICE). v5 breaking changes vs v4: series via `chart.addSeries(SeriesType, …)`, markers via the `createSeriesMarkers` plugin (not `series.setMarkers()`), and new **multi-pane** support. No built-in volume profile → custom series primitive. *Advanced Charts* is the heavier alternative (built-in studies/drawing tools, needs a Datafeed adapter + access request).
- **Java 25** — LTS; virtual threads, structured concurrency, scoped values, pattern matching, records, sealed types.
