# NEPSE Floorsheet Analytics & Signal Platform
### Implementation Plan — Phased (Backend & Frontend tracks)

**Companion to:** `nepse-signal-platform-architecture.md` (the design of record). This plan turns that design into an ordered, phased delivery schedule. It does **not** restate the architecture — it sequences it.

**Stack recap:** Java 25 · Spring Boot 4.0 · Spring Batch · PostgreSQL/TimescaleDB · Redis · Object store (S3/MinIO) · Angular 22 · TradingView Lightweight Charts™ v5 · Ta4j-core 0.22.x.

---

## How to read this plan

- Work is split into **10 phases (Phase 0 → Phase 9)**. Each phase has a **Backend track** and a **Frontend track** that are planned independently but delivered together.
- Each phase states: **Goal**, **Backend deliverables**, **Frontend deliverables**, **Dependencies**, **Parallelization note** (what the frontend can start before the backend is done, using contract-first mocks), and a **Definition of Done (DoD)**.
- **Contract-first rule:** every phase begins by freezing the OpenAPI slice (springdoc) and the TypeScript DTOs for that phase's endpoints. The frontend builds against a mock implementing that contract, then swaps to the live API. This is what lets the two tracks run concurrently.
- Phases are **vertically sliced** where possible: each one should end with something demonstrable end-to-end, not just a backend layer with no UI.
- Mapping to the architecture doc's build steps is noted as `(arch §10.x)` / `(arch §F#)`.

### Dependency overview

```
Phase 0  Foundations ─────────────────────────────┐
Phase 1  IAM + Reference/Calendar ────────────────┤ (everything depends on 0 & 1)
Phase 2  Ingestion ───────────────────────────────┤
Phase 3  Market Data ◄── needs 2 (trades)         │
Phase 4  Indicators  ◄── needs 3 (candles)        │
Phase 5  Signals     ◄── needs 4 (indicators) + 3 (vol profile / broker flow)
Phase 6  Backtesting ◄── needs 5 (confluence) + 4 (Ta4j path)
Phase 7  Screeners/Dashboard ◄── needs 3 + 4 + 5
Phase 8  Notifications/Realtime/Watchlist ◄── needs 5 (events)
Phase 9  Orchestration + Hardening ◄── ties 2–8 together
```

### Track sequencing at a glance

| Phase | Backend focus | Frontend focus | Demo at end of phase |
|---|---|---|---|
| 0 | Skeleton, CI, ArchUnit, DB/Redis/object store | App shell, design system, routing, http layer | Empty app boots, health green, CI runs Ta4j smoke test |
| 1 | Auth (JWT), reference, calendar | Login/guards, instrument & broker browse | Log in; browse scrips, brokers, trading-day calendar |
| 2 | Ingestion + Spring Batch + batch upload | Admin ingestion console | Upload a day (and a backfill batch); watch it ingest |
| 3 | Candles, VWAP, vol profile, broker flow, NEPSE aggregate | Price chart (candles+volume), broker flow, movers | See OHLC chart + market summary for an ingested day |
| 4 | Ta4j engine + custom-study SPI + 4 studies | Catalog-driven indicator dialog, panes, profile primitive | Add RSI/MACD/HMA/UT Bot/SMC to a chart |
| 5 | Strategies S1–S8, confluence S9, reasons | Signal lists + reasons UX + chart markers | View daily BUY/SELL/HOLD with "why" |
| 6 | Ta4j backtester + NEPSE costs + portfolio | Backtest config + results + compare | Run and compare backtests with equity curves |
| 7 | Screener module, RVOL, price-drop, caching | Day dashboard + price-drop screener | One-glance EOD dashboard + sharp-drop screener |
| 8 | Alerts, notifications, STOMP/SSE, watchlist | Notification center, toasts, watchlist, alerts | Real-time signal toast on pipeline completion |
| 9 | Scheduler, reprocess, security/obs hardening | Polish, a11y, perf, e2e | 15:01 NPT auto-run; full secure-by-default pass |

---

## Phase 0 — Foundations & platform skeleton

**Goal:** A deployable empty system with all infrastructure, CI, and cross-cutting conventions in place. No business features.

### Backend track *(arch §10.1)*
- Spring Boot 4.0.x project, Java 25 toolchain, **virtual threads enabled** (`spring.threads.virtual.enabled=true`).
- Dependencies: `spring-boot-starter-batch`, `org.ta4j:ta4j-core` (≥ 0.22.7, pinned), `archunit-junit5` (test), Actuator + Micrometer, springdoc OpenAPI.
- **`platform` module** (shared kernel): error model (`@RestControllerAdvice`), pagination types, NPT clock, calendar utilities stub, config, OpenAPI base.
- Infra wiring: Postgres + **Flyway** (per-module migration folders by naming convention), Redis client, object-store (S3/MinIO) client.
- **ArchUnit boundary tests**: "no module may depend on another module's `internal` package"; module package skeletons created so the rules have something to guard.
- **CI**: build + test on Java 25; **Ta4j smoke/regression test** of the indicators we will use (per the Java-25 caveat — Ta4j doesn't yet officially support 25).
- Layered container image; profiles for local/dev/prod; secrets sourced from env/secret manager (never in code/config).

> **Build note (from memory):** build needs **JDK 25** — default `JAVA_HOME` is JDK 17; export JDK 25 before `./mvnw`. Backend uses Jackson 3 (`tools.jackson`, not `com.fasterxml`).

### Frontend track *(arch §12)*
- Angular 22 app scaffold (standalone components, `OnPush` default), **TypeScript 6, Node 26**.
- Routing shell, layout (nav/shell/error boundary), theme/design system, loading/empty/error states.
- HTTP layer conventions: `httpResource`/`rxResource` (HttpClient Fetch default), interceptors for auth header + error normalization, environment config.
- Test runner per the frontend stack (**Vitest**), flat file naming, install `@angular/animations`.
- Shared DTO package generated from the backend OpenAPI (codegen step in CI).

> **Frontend gotchas (from memory):** Vitest runner, flat naming, install `@angular/animations`, SSE via `fetch-event-source`.

**Dependencies:** none.
**Parallelization:** fully parallel — no shared contract yet beyond error/pagination envelope, which is defined first thing.
**DoD:** app boots and serves an empty authenticated-less shell; `/actuator/health` green; CI green including the Ta4j Java-25 smoke test; ArchUnit rules active; a trivial endpoint round-trips through the OpenAPI→DTO codegen.

---

## Phase 1 — Identity (IAM), Reference & Trading Calendar

**Goal:** Users can authenticate; the system knows its instruments, brokers, and trading-day calendar — the master data every later phase depends on.

### Backend track *(arch §10.2, §10.11, §F2, §F10)*
- **IAM** *(arch §F10)*: register/login, JWT **access + refresh**, refresh rotation + logout (refresh invalidation); roles `USER` / `ANALYST` / `ADMIN`; **Argon2id** password hashing; Spring Security 6 method-level role gating.
- **Reference** *(arch §F2)*: `instrument`, `broker`, `trading_day` entities + repositories + seed loaders; new-symbol auto-discovery hook (provisional record flag) for later ingestion.
- **Calendar service**: trading-day look-backs ("previous close", N-trading-day windows skip holidays). This is consumed by virtually every downstream module — get it right early.
- Endpoints: `/auth/*`, `/users/me`, `/instruments*`, `/sectors`, `/brokers*`, `/calendar/*`.

> **Test note (from memory):** under full `@SpringBootTest` with the resource server, `@WithMockUser` gives 401 — use the `jwt()` request post-processor in MockMvc tests.

### Frontend track *(arch §12, Decision B)*
- Login / register / logout flows; **token storage = httpOnly+Secure+SameSite cookie for refresh + short-lived in-memory access token** (arch Decision B, option 1); CSRF protection paired with the cookie.
- Route guards by role; auth interceptor + silent refresh; "current user" signal store.
- Reference browse UI: instrument list/filter (sector, status, search), instrument detail, broker registry, trading-day calendar viewer.

**Dependencies:** Phase 0.
**Parallelization:** frontend builds auth + reference screens against the mocked OpenAPI slice; swaps to live once `/auth/*` and `/instruments*` land. Token-storage decision (cookie vs in-memory) is fixed up front so the interceptor is built once.
**DoD:** a user can register, log in, refresh, and log out; role gating verified (admin-only endpoints reject non-admins); instruments/brokers/calendar browseable; calendar service returns correct N-trading-day look-backs across a holiday.

---

## Phase 2 — Ingestion pipeline (the careful part)

**Goal:** Untrusted daily CSV(s) become clean, deduplicated, queryable trades — with safe multi-file backfill — orchestrated by Spring Batch and restartable from the failed step.

### Backend track *(arch §10.3, §3, §F1, Decisions A & D)*
- **Filename contract:** validate `^\d{4}-\d{2}-\d{2}\.csv$`; parse date; **never use raw filename as a path** — store under controlled key `raw/{date}/{contentHash}.csv`.
- **Streaming parser** (univocity / Commons CSV) — bounded memory, never load whole file.
- **Defensive parsing** *(arch §1.2)*: whitespace normalize → strip comma-after-date → ordered `DateTimeFormatter` list (`H:mm:ss:SSS a` etc.) → range/consistency validation → **filename-date vs row-timestamp-date cross-check** → dedup on `contract_id` → quarantine rejects with reason codes (one bad row never sinks the batch).
- **Amount cross-check** with configurable tolerance; CSV/formula-injection neutralized on any future export (arch Decision A).
- **Batch upload** *(arch §F1, Decision D option 1)*: `POST /ingestion/uploads/batch` multipart with per-batch caps (file count, total bytes); reject malformed names / duplicate dates within batch; create `ingestion_batch` + per-file `ingestion_job`; return `202 + batchId`; process async, **sorted oldest-first**.
- **Spring Batch jobs:** `MultiResourceItemReader` / partitioned ingest; skip policy → `ingestion_rejection`; JobRepository = restart-from-failed-date. Downstream indicator/signal phases iterate **dates ascending** (parallel across symbols).
- **Backfill semantics:** `suppressNotifications` flag for historical dates (only the latest/live date fans out).
- Archive raw (content hash) before parse; per-file job counts roll up to batch; publish `TradesIngestedEvent` per date.
- Endpoints: `/ingestion/uploads`, `/uploads/batch`, `/trigger`, `/webhook` (HMAC-signed, arch Decision C), `/batches*`, `/jobs*`, `/reprocess`.

### Frontend track *(arch §12, admin)*
- **Admin ingestion console** (role-gated): single-file upload and **multi-file batch upload** with client-side filename validation and cap hints.
- Batch list + batch detail with **per-date progress** (queued/ingested/aggregated/signaled/failed); per-file results (accepted/rejected/duplicate, row counts).
- Rejection report viewer (downloadable); retry-failed-dates action; reprocess-a-date action.
- Pipeline status surface (reads Batch execution state — wired fully in Phase 9, stubbed here).

**Dependencies:** Phase 1 (reference for symbol resolution; admin role).
**Parallelization:** frontend builds the upload + batch-status screens against mocked `202`/batch-status responses; the messy-sample golden files (arch §10.13) double as fixtures the frontend can use for the rejection viewer.
**DoD:** uploading a clean day persists trades idempotently (re-upload replaces, never duplicates); a backfill batch ingests oldest-first; dirty sample rows (arch §1.2) land in quarantine with reason codes, not a failed batch; killing the JVM mid-batch and restarting resumes from the failed date; admin UI shows accurate per-date/per-file progress.

---

## Phase 3 — Market data & aggregation

**Goal:** Derive and serve all price/volume structure — candles, VWAP, exact volume profile, broker flow, and the whole-market `NEPSE` aggregate — and chart the basics.

### Backend track *(arch §10.4, §6.2, §6.3, §F3)*
- **Daily candle builder**: time-ordered O/H/L/C, volume, turnover, trades_count, VWAP, prev_close, change%.
- **Volume profile builder** *(arch §6.2)*: exact volume-at-price → POC, VAH, VAL (70% rule), HVN/LVN; composite (range) profile; stored as JSONB.
- **Broker flow builder** *(arch §6.3)*: per-broker buy/sell qty & amount, net, top-N share, HHI concentration; top accumulating/distributing.
- **Intraday candles** (retrospective, optional): configurable interval bucketing, gaps preserved.
- **Market aggregate `NEPSE`** *(arch §F3)*: sum all symbols → total turnover/volume/trades + advances/declines/unchanged; market-wide broker flow; **cap-weighted index proxy** if `listed_shares` present (labeled approximate); hook for ingesting official index; **no volume profile for `NEPSE`**.
- Movers/summary queries; publish `MarketDataReadyEvent`.
- Endpoints: `/market/trades`, `/candles`, `/candles/intraday`, `/vwap`, `/volume-profile`, `/broker-flow`, `/broker-flow/top`, `/movers`, `/summary`, `/aggregate`.

### Frontend track *(arch §12, §F7 partial)*
- **First Lightweight Charts v5 integration**: candlestick series + volume histogram pane; business-day (`YYYY-MM-DD`) time axis; enable `attributionLogo` (Apache-2.0 attribution requirement).
- Volume-profile **custom series primitive** scaffolding (right-aligned histogram + POC/VAH/VAL guides) — reused/extended in Phase 4.
- Broker-flow panel (net buyers/sellers, concentration); movers & market-summary widgets; `NEPSE` whole-market view (totals/breadth; index line when present, profile hidden).

**Dependencies:** Phase 2 (needs ingested trades).
**Parallelization:** chart and panels build against recorded sample payloads (one ingested day exported as JSON) before the live endpoints are final. The volume-profile primitive is non-trivial — start it here even though more series types arrive in Phase 4.
**DoD:** for an ingested day, candles/VWAP/volume-profile/broker-flow are correct (spot-checked against the floorsheet, e.g. Amount cross-check); `NEPSE` aggregate matches summed totals and breadth; chart renders candles+volume; volume-profile primitive draws POC/VAH/VAL.

---

## Phase 4 — Indicator engine (Ta4j + custom-study SPI)

**Goal:** A broad, extensible indicator catalog — Ta4j built-ins plus the four shipped custom studies — computed deterministically and rendered by output kind.

### Backend track *(arch §10.5, §6.5, §F4)*
- **`candles → Ta4j BarSeries` adapter** (`BaseBarSeriesBuilder`); **`DecimalNum`** for the canonical/signal path, `DoubleNum` for chart overlays.
- **`IndicatorService`** resolving the catalog via Ta4j `Indicator<Num>` (RSI, EMA/SMA/WMA, MACD, Bollinger, ATR, ADX/DMI, Supertrend, OBV, MFI, CMF, A/D, pivots, Donchian, Keltner, etc.). Volume profile & broker flow **stay custom** in `marketdata`.
- **Custom-study SPI** *(arch §6.5)*: `CustomIndicator` + `IndicatorDescriptor` (param schema, `OutputKind`, `feedsSignalEngine`) + sealed `IndicatorResult` (`Lines`/`Signals`/`Markers`/`Zones`); `CustomIndicatorRegistry` auto-discovers `@Component` beans → `/indicators/catalog`.
- **Four shipped studies:** **HMA** (`LINE`), **UT Bot** (`SIGNAL`, recursive ATR stop), **Inside Hammer** (`MARKER`), **SMC** (`ZONE`: swing → BOS/CHoCH, order blocks, FVG, liquidity, premium/discount). Expose numeric ones as `Indicator<Num>` (so they backtest); set `feedsSignalEngine` to wire votes later.
- Persist canonical per-(symbol,date) snapshot (JSONB + promoted RSI/EMA/ATR columns); **cache parametrized series in Redis** keyed `(symbol, indicator, paramsHash, lastDate)`; explicit eviction on recompute. Publish `IndicatorsComputedEvent`.
- Endpoints: `/indicators/catalog`, `/indicators`, `/indicators/{symbol}/latest`, `/indicators/compute`, `/indicators/custom`, `PATCH /indicators/custom/{id}`, `POST /indicators/custom/definitions` (validated JSON config only — arch Decision E option 1).

> **Indicator note (from memory):** add numeric indicators via `IndicatorType` + `IndicatorResolver`; **SMC is a separate overlay module, not a numeric indicator**. Ta4j returns `NaN` during indicator warm-up windows — guard for it.

### Frontend track *(arch §12, §F7)*
- **Catalog-driven "Add indicator" dialog**: form generated from each study's **param schema** (`/indicators/catalog`) — any future study appears automatically.
- **Output-kind dispatch** in the chart: `LINE`→ line series; `BAND`→ multiple lines; `SIGNAL`→ line + `createSeriesMarkers`; `MARKER`→ `createSeriesMarkers`; `ZONE`→ custom box/ray/label primitives (SMC). Multi-pane (v5) for RSI/MACD/volume below price.
- Finalize the volume-profile primitive started in Phase 3.

**Dependencies:** Phase 3 (needs candles).
**Parallelization:** the catalog contract is the key handshake — freeze `IndicatorDescriptor` JSON early so the dialog and output-kind renderers are built against it while studies are implemented backend-side.
**DoD:** catalog lists built-ins + 4 custom studies with param schemas; `/indicators/compute` returns correctly typed results for each output kind; warm-up `NaN` handled (no garbage at series start); chart renders a `LINE` (HMA), `SIGNAL` (UT Bot), `MARKER` (Inside Hammer), and `ZONE` (SMC) study; Redis cache hit on repeat parametrized request.

---

## Phase 5 — Signal engine (BUY/SELL/HOLD + reasons)

**Goal:** A daily directional signal per scrip with explicit, auditable, human-readable reasons.

### Backend track *(arch §10.6, §6.1, §6.4, §F5)*
- **`SignalStrategy`** interface + `StrategyVote` (vote ∈ [−1,+1], confidence ∈ [0,1]) + structured **`Reason`** record.
- Implement **S1, S2, S3, S5, S6, S7** with Ta4j `Indicator`s + `Rule`s (map boolean trigger → graded vote + reason). Implement **S4 (volume profile)** and **S8 (broker flow)** as custom strategies reading floorsheet-derived structures. Pluggable studies with `feedsSignalEngine=true` contribute votes automatically.
- **Confluence scorer S9** *(arch §6.4)*: config-driven weights → score ∈ [−100,+100] → action via thresholds; store full vote vector + top reasons + dissents. **Expose score as a custom Ta4j `Indicator<Num>`** (needed for Phase 6 backtesting). Optional regime gate using EMA/ADX on `NEPSE`.
- Narrative templating for reasons; persist `signal` (action, score, confidence, votes JSONB, reasons JSONB, snapshot ref). Publish `SignalsGeneratedEvent` (AFTER_COMMIT) — the fan-out point for Phases 6–8.
- Endpoints: `/signals/latest`, `/signals`, `/signals/{id}`, `/signals/symbol/{symbol}`, `POST /signals/generate` (admin), `/strategies*`.

### Frontend track *(arch §12, §F5)*
- **Signal lists**: latest run's BUY / SELL / HOLD, filterable by date/action/sector/minScore.
- **Signal detail / Reasons UX**: per-strategy contribution bars + narrative; "4 of 6 trend/volume bullish; broker flow neutral; mean-reversion dissenting" breakdown.
- **Chart markers**: BUY/SELL/HOLD via `createSeriesMarkers`; clicking a marker opens the reasons panel; markers-only refresh endpoint used to avoid refetching candles.
- Strategy config browse (analyst can view; editing UI optional this phase).

**Dependencies:** Phase 4 (indicators) + Phase 3 (volume profile / broker flow for S4/S8).
**Parallelization:** the `Reason` and vote-vector DTOs are the handshake; the reasons panel can be built against a fixture signal while the scorer is tuned.
**DoD:** a daily run produces BUY/SELL/HOLD per scrip; each signal carries structured reasons rendered as text + data; the confluence score is reproducible and exposed as a Ta4j indicator; markers appear on the chart and open the reasons panel.

---

## Phase 6 — Backtesting engine

**Goal:** Validate strategies on history with realistic NEPSE costs and no look-ahead bias.

### Backend track *(arch §10.7, §7, §F6)*
- Drive per-symbol runs with Ta4j **`BarSeriesManager`** (single) / **`BacktestExecutor`** (parallel sweeps) → `TradingRecord`.
- **Confluence backtests:** use the Phase-5 confluence-score indicator with `Over/UnderIndicatorRule` at ±thresholds.
- **Bias control:** signal-at-close / **fill-next-open** (shift Ta4j's `TradeOnCurrentCloseModel`); survivorship via reference status.
- **Custom layer:** NEPSE cost model (tiered brokerage, SEBON fee, flat per-scrip DP, CGT, slippage — **rates in config, not code**) + shared-capital **portfolio** aggregation across the BUY list.
- **Metrics:** Ta4j `AnalysisCriterion`s (return, drawdown, win ratio, profit factor, position counts) + **custom criteria** (Sortino, CAGR, exposure, cost-adjusted). Equity & drawdown curves; per-trade log with entry/exit reasons; run versioning + compare.
- Listener on `SignalsGeneratedEvent` rolls forward rolling backtests for the BUY list → `BacktestUpdatedEvent`.
- Endpoints: `POST /backtests`, `/backtests`, `/{id}`, `/{id}/metrics`, `/{id}/equity-curve`, `/{id}/trades`, `/compare`, `DELETE /{id}`.

### Frontend track *(arch §12)*
- **Backtest config form**: strategy/config, symbols, date range, starting capital, position sizing, cost model.
- **Results view**: metrics cards, equity & drawdown curves (charted), trade log with entry/exit reasons.
- **Compare view**: multiple runs side-by-side (leaderboards for parameter sweeps).

**Dependencies:** Phase 5 (confluence score) + Phase 4 (Ta4j path).
**Parallelization:** results/compare UI builds against a recorded backtest result JSON; long-running runs return async + status, so the UI handles a pending state from the start.
**DoD:** a backtest over history produces correct metrics with NEPSE costs applied and no same-bar look-ahead; equity/drawdown curves and trade log render; two runs compare; **deterministic backtest regression test** passes in CI (arch §10.13).

---

## Phase 7 — Screeners & day dashboard

**Goal:** One-glance EOD activity view and the sharp price-drop screener, pre-warmed and cached.

### Backend track *(arch §10.8, §6.6, §F11, §F12)*
- New **`screener` module** composing marketdata + indicator + signal (read-only; ArchUnit permits dependence on their **published** APIs only).
- **RVOL** (ratio + z-score) from volume-SMA + windowed stddev; **activity rankings** (turnover/volume/trades, high & low) from `daily_candle`; **price-drop** metrics (%Δ, drawdown-from-window-high, ATR-normalized sharpness) from candles + ATR.
- Trading-day windowing via calendar; **liquidity floor** on by default; **warm-up/insufficient-history guard**; exclude no-trade scrips from spikes (surface separately).
- **Pre-warm** day dashboard + 30/45/60 drop lists on `SignalsGeneratedEvent`; Redis-cache per date; custom windows on demand.
- Annotate each row with current signal + chart link; register alert types `RVOL_SPIKE` / `RVOL_DROP` / `PRICE_DROP` (consumed in Phase 8).
- Endpoints: `/dashboard/day`, `/screener/active`, `/screener/relative-volume`, `/screener/price-drop`, `/screener/scan`, `/screener/definitions*`.

### Frontend track *(arch §12, §F11, §F12)*
- **Day Transaction Analysis dashboard** (F11): tiles for high/low trade (turnover/volume/trades), RVOL spikes/drops (ranked by z-score, labeled with ratio), market breadth / sector heat; window selector (default 20).
- **Sharp Price-Drop screener** (F12): window selector (30 / 45 / 60 / custom 5–365), metric selector (pctchange/drawdown/sharpness), sector + liquidity filters; columns incl. %Δ, drawdown, sharpness, RVOL context, current signal, chart link.
- Saved screens UI; **one-click "turn this row into an alert"** (pre-wires Phase 8 alerts).

**Dependencies:** Phase 3, 4, 5.
**Parallelization:** dashboard/screener bind via `httpResource`; build against cached sample payloads. Every row deep-links to the Phase-3/4/5 chart and signal views already built.
**DoD:** dashboard loads instantly from cache for the latest day; RVOL spikes ranked by z-score; price-drop presets cached, custom windows computed on demand with liquidity floor + warm-up guard; newly-listed scrips flagged "insufficient history"; rows link to chart and show current signal.

---

## Phase 8 — Notifications, realtime & watchlists

**Goal:** Push the day's relevant signals to users in the web app, with durable delivery and watchlist/alert management.

### Backend track *(arch §10.10, §10.11, §F8, §F9)*
- **Alert-rule evaluator** on `SignalsGeneratedEvent`: match signals against watchlists + user alert rules (incl. `RVOL_SPIKE`/`RVOL_DROP`/`PRICE_DROP` from Phase 7); dedup per (user, signal).
- **Notifications**: persisted in the same step that creates them (outbox via `notification.sent` flag); dispatcher pushes unsent rows (survives crash); **backfilled historical dates do NOT fan out** (arch §3, F1.5).
- **Realtime**: STOMP WebSocket (`/ws`: `/topic/signals`, `/user/queue/notifications`, `/topic/pipeline-status`) + **SSE fallback** (`/notifications/stream`); Redis backplane if multi-instance.
- **Watchlist / paper portfolio** *(arch §F8)*: CRUD watchlists & items; optional paper trades valued at latest close.
- Endpoints: `/notifications*`, `/alerts*`, `/watchlists*`.

### Frontend track *(arch §12)*
- **Notification center** (read/unread feed) + real-time **toasts**; subscribe to STOMP topics; **SSE fallback via `fetch-event-source`** (memory note).
- **Watchlist UI**: manage lists/items; optional paper P&L.
- **Alert management**: create/edit/delete rules; the Phase-7 "one-click alert" lands here.

**Dependencies:** Phase 5 (events) + Phase 7 (screener alert types) + Phase 1 (users/watchlists).
**Parallelization:** notification feed + toasts build against a mock WS/SSE stream; the watchlist/alert CRUD against the OpenAPI mock.
**DoD:** completing a daily run pushes a toast + persists a notification; killing the dispatcher and restarting still delivers unsent rows; backfill produces no user notifications; SSE fallback works when WS is unavailable; watchlist/alert CRUD round-trips.

---

## Phase 9 — Orchestration, scheduling & hardening

**Goal:** The pipeline runs itself daily, is fully restartable and observable, and passes a secure-by-default review end-to-end.

### Backend track *(arch §10.12, §10.13, §11)*
- **Scheduling**: `@Scheduled` **15:01 NPT** trigger (+ **ShedLock** if replicated) launches the Spring Batch job; admin manual trigger + reprocess; **`/system/pipeline/status`** reads Batch job/step execution state; restartability from JobRepository.
- **Observability**: Micrometer metrics + tracing; structured logging with a **correlation/trace id per pipeline run**; Actuator dashboards; Redis caching with explicit eviction on recompute.
- **Security hardening (arch §11 baselines)**: HTTPS-only; CORS locked to the Angular origin; per-user + per-IP **rate limiting** (Redis) on auth + heavy endpoints; secrets from env/secret manager (audit none in code/config/logs/tests); least-privilege DB role; validate/bound every query param (date ranges, `bins`, page size); HMAC + timestamp on the scraper webhook (Decision C); confirm custom-indicator definitions accept **config only, never code** (Decision E).
- **Testing pass (arch §10.13)**: ArchUnit boundary tests, per-module `@SpringBootTest` slices, **Testcontainers** (Postgres/Redis), **`@SpringBatchTest`** incl. restart-from-failed-step, golden-file ingestion tests (the messy sample rows), deterministic backtest regression.

### Frontend track *(arch §12)*
- Pipeline-status surface wired to live `/system/pipeline/status` + `/topic/pipeline-status`.
- **Accessibility** audit (Angular 22 Aria), responsive/mobile pass, performance (lazy-load heavy chart/backtest modules via `@Service` + `injectAsync`), error/empty-state polish.
- **E2E** suite covering the critical journeys (login → ingest → chart → signal → backtest → alert).

**Dependencies:** Phases 2–8.
**Parallelization:** hardening is cross-cutting; security review and a11y/perf run in parallel on the now-complete features.
**DoD:** the 15:01 NPT run executes the full pipeline unattended and restarts cleanly from a forced mid-step failure; pipeline status visible live; **security baseline checklist all green**; full test matrix (ArchUnit + slices + Testcontainers + `@SpringBatchTest` + backtest regression) green in CI; e2e journeys pass; a11y/perf budgets met.

---

## Cross-cutting conventions (apply in every phase)

- **Contract-first**: freeze the OpenAPI slice + DTO codegen before parallel work; frontend mocks the contract, then swaps to live.
- **Idempotency**: re-running any date replaces that date's derived rows; never duplicates (`contract_id` upsert is the backbone).
- **Trading-day math**: every look-back counts trading days via the calendar (Phase 1) — never naive date subtraction.
- **Security defaults (arch §11)**: treat scraped CSV as hostile; secure token storage; signed webhook; config-only custom studies; secrets never in code/config/logs/tests. When a security/speed tradeoff appears, choose security and document it.
- **Modularity**: ArchUnit guards module boundaries from Phase 0 onward — a boundary violation fails the build.
- **Observability from day one**: correlation id per pipeline run; Actuator/Micrometer wired in Phase 0, enriched per phase.

## Risk register (watch items)

| Risk | Phase | Mitigation |
|---|---|---|
| Ta4j not officially Java-25 supported yet | 0, 4, 6 | Pin version; CI smoke/regression test the indicators used; track the release that declares support |
| Messy/ambiguous CSV timestamps & formats | 2 | Ordered formatter list + quarantine + golden-file tests with the real messy sample |
| Look-ahead bias in backtests | 6 | Signal-at-close / fill-next-open; deterministic regression test |
| Volume-profile custom primitive complexity (LWC has none) | 3, 4 | Start the primitive in Phase 3, finalize in Phase 4 |
| Backfill spamming stale notifications | 2, 8 | `suppressNotifications` for historical dates; only latest/live date fans out |
| Build on wrong JDK | 0 | JDK 25 required; documented in build setup |
| `NaN` from Ta4j warm-up windows | 4, 5 | Guard/suppress signals until warm-up satisfied |

---

*This plan sequences `nepse-signal-platform-architecture.md`. When the two disagree, the architecture document is authoritative — update this plan to match, not the reverse.*
