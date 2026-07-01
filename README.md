# NEPSE Floorsheet Analytics & Signal Platform

EOD floorsheet analytics, technical indicators, daily BUY/SELL/HOLD signals, backtesting and
screeners for the Nepal Stock Exchange. Modular-monolith backend (Spring Boot 4 / Java 25) + Angular 22 SPA.

- **Design of record:** [`nepse-signal-platform-architecture.md`](nepse-signal-platform-architecture.md)
- **Delivery plan:** [`nepse-signal-platform-implementation-plan.md`](nepse-signal-platform-implementation-plan.md)

## Status: Phase 9 — Orchestration, Scheduling & Hardening ✅ (platform feature-complete)

Ties Phases 2–8 together with the daily trigger, status, and the secure-by-default pass.

### Backend
- **Scheduling:** `@Scheduled` **15:01 NPT** daily run (`IngestionScheduler`) re-runs the day from the archived raw file, driving the ingest → aggregate → indicators → signals → notify chain; no-ops gracefully if the scraper file isn't there yet (config-gated; ShedLock noted for replicas).
- **Pipeline status:** `PipelineStatusTracker` listens to the four stage events and serves `/system/pipeline/status` (last date + timestamp per stage).
- **Security hardening (§11):** per-IP **token-bucket rate limiter** on the auth endpoints (429 + `ApiError`, unit-tested), **security headers** (HSTS, X-Content-Type-Options, frame-deny), tightened permit-list (only `/system/ping` public), CORS locked to the Angular origin, secrets from env, HMAC webhook, config-only custom studies.
- **Observability:** a **correlation-id filter** (`X-Request-Id` + MDC `traceId`) with a trace-aware log pattern; Actuator/Micrometer from Phase 0.
- 61/61 non-integration tests green; ArchUnit boundary rule green across all 12 modules.

### Frontend
- **Pipeline-status** page (admin) showing the stage flow; nav polish.

---

## Charting API (F7) ✅

The dedicated `charting` module — read-only composition over marketdata + indicator + signal.
- **`GET /charts/{symbol}`** returns **one payload** whose arrays map 1:1 to Lightweight Charts series: candlesticks, a volume histogram, requested `indicators=` overlays (tagged by output kind), the `overlays=volprofile` bins + POC/VAH/VAL, and signal markers — **ETag-validated** (unchanged chart → 304). **`GET /charts/{symbol}/markers`** returns markers only.
- **`GET /charts/{symbol}/snapshot.png`** — a **server-side PNG** (candles + volume + BUY/SELL/HOLD markers) rendered headless with Java2D (no browser dependency). Signal **notifications carry the scrip symbol**, so the notification feed renders a chart thumbnail for each alert (fetched as an authed blob).
- New published read-ports enable it without touching internals: `indicator.api.IndicatorEngine`, `marketdata` volume-profile bins + range lookup, `signal.api.SignalReader.markersFor`. ArchUnit-clean.
- Frontend `price-chart` loads **everything** — candles, volume, volume profile, and **signal markers (with ids)** — from the single composite call; clicking a marker emits its id straight to the reasons panel (the separate `/signals/symbol` fetch is gone). Interactive study overlays use `/indicators/compute` and render by output kind, with **oscillators (RSI/MACD/ADX/MFI/ATR) in their own sub-panes** below price (v5 multi-pane); moving averages / Bollinger / Supertrend / SMC overlay the price pane. *(UDF datafeed endpoints for TradingView Advanced Charts remain optional/deferred — Lightweight Charts needs no datafeed.)*

---

## Status: Phase 8 — Notifications, Realtime & Watchlists ✅

Push the day's relevant signals to users, in-app and in real time.

### Backend (`watchlist/` + `notification/` modules)
- **Watchlists** (F8): user-scoped CRUD (`/watchlists`, items) + a published `watchlist.api.WatchlistReader` (watchers-by-symbol).
- **Alert evaluator** on `SignalsGeneratedEvent`: matches the day's signals against **watchlist symbols** (BUY/SELL) + user **alert rules** (`SIGNAL_ACTION`; `RVOL/PRICE_DROP` types accepted for forward-compat), **deduped per (user, signal)**. **Backfilled dates are skipped** so a backfill never spams users. Pure `AlertMatcher` is unit-tested.
- **Durable outbox:** notifications are persisted in the same step that creates them (`sent` flag, Flyway V10); a dispatcher pushes unsent rows and flips the flag; a **startup recovery runner** re-pushes anything a crash left unsent.
- **Realtime = SSE:** a per-user `SseHub` stream (`/notifications/stream`), consumed by the SPA with its bearer token. STOMP WebSocket is the architecture's multi-client primary and a documented follow-on.
- **Endpoints:** `/notifications` (feed), `/unread-count`, `PATCH /{id}/read`, `/read-all`, `/stream`; `/alerts` CRUD; `/watchlists` CRUD. 59/59 non-integration tests green; ArchUnit clean.

### Frontend
- **Notification center**: a shell **bell with unread badge**, a feed page (mark read / all), and **live SSE** via `@microsoft/fetch-event-source` (bearer token) raising toasts on new signals.
- **Watchlist management** (create / add-remove symbols / delete) and **alert-rule management** (signal-action alerts + enable/disable). Session drives the SSE connect/disconnect.

## Status: Phase 7 — Screeners & Day Dashboard ✅

One-glance EOD activity view + the sharp price-drop screener.

### Backend (`screener/` module, read-only)
- **Pure metrics (unit-tested, §6.6):** RVOL (ratio + volume z-score), and the three drop lenses — point-to-point %Δ, drawdown-from-window-high, and ATR-normalized sharpness.
- **`ScreenerService`:** high/low-trade rankings (turnover/volume/trades), RVOL scan (spikes ranked by **z-score**, drops by ratio), and the sharp price-drop screener — every scan applies the **liquidity floor** + **warm-up guard** (`insufficientHistory` flag), counts windows as trading-day candle rows, and annotates each row with the scrip's **current signal**.
- **Cross-module reads** via new published ports — `marketdata.api.MarketBoard` (board + summary) and `signal.api.SignalReader` — plus `indicator.api.BarSeriesFactory` for the ATR in sharpness. ArchUnit-clean.
- **Pre-warm cache:** an `@TransactionalEventListener` on `SignalsGeneratedEvent` warms the day dashboard + 30/45/60 drop presets into a pluggable cache (in-memory default, Redis drop-in); custom windows compute on demand.
- **Endpoints:** `/dashboard/day`, `/screener/active`, `/screener/relative-volume`, `/screener/price-drop`.
- 55/55 non-integration tests green.

### Frontend
- **Day dashboard**: breadth + high/low-trade + RVOL spike/drop tiles, each row deep-linking to the chart and showing the current signal.
- **Sharp price-drop screener**: window presets 30/45/60 + custom, metric selector (%Δ / drawdown / sharpness), RVOL context, `insufficient history` flag, signal chips.

## Status: Phase 6 — Backtesting Engine ✅

Validate the confluence strategy on history with realistic NEPSE costs and no look-ahead.

### Backend (`backtest/` module)
- **Confluence score as a Ta4j `Indicator<Num>`** (the §6.4 hook): the `signal.api.ConfluenceModel` port snapshots weights/strategies into a scorer the `ConfluenceScoreIndicator` sweeps per bar — so the whole confluence model backtests via `Over/UnderIndicatorRule` at ±thresholds. (Enabled by refactoring `SymbolContext` to carry an evaluation index.)
- **No look-ahead:** Ta4j `BarSeriesManager` + **`TradeOnNextOpenModel`** (signal-at-close → fill-next-open).
- **NEPSE cost model** (unit-tested, config-driven): tiered brokerage + SEBON fee both sides, flat per-scrip DP on sells, CGT on realized gains, slippage — applied in a custom layer Ta4j's flat model can't express.
- **Metrics** from the cost-adjusted trade log: total return, CAGR, max drawdown, win rate, profit factor, exposure, Sortino; **equity + drawdown curves** and a **trade log with entry/exit reasons**.
- **Persistence** (Flyway V8: `backtest_run`/`result`/`trade`), synchronous runs, `BacktestUpdatedEvent`. **Endpoints:** `POST /backtests`, `GET /backtests`, `/{id}`, `/{id}/metrics`, `/{id}/equity-curve`, `/{id}/trades`, `POST /compare`, `DELETE /{id}`.
- 53/53 non-integration tests green; ArchUnit clean.

### Frontend
- **Backtest page:** config form (symbol, range, capital, thresholds) → run; recent-runs list with **multi-select compare**.
- **Result page:** metric cards, an **equity/drawdown chart** (Lightweight Charts area + histogram), and the trade log.

## Status: Phase 5 — Signal Engine ✅

A daily BUY/SELL/HOLD per scrip with explicit, auditable reasons.

### Backend (`signal/` module)
- **SPI:** `SignalStrategy` + `StrategyVote` (vote ∈ [-1,+1], confidence ∈ [0,1]) + structured `Reason`; `SymbolContext` (Ta4j series via the published `BarSeriesFactory` + volume-profile/broker-flow views from `marketdata.api.MarketAnalytics`).
- **Eight strategies** (unit-tested): S1 trend (EMA+ADX), S2 mean-reversion (RSI+Bollinger), S3 breakout (Donchian+volume), S5 MACD, S6 Supertrend, S7 money-flow (CMF/MFI), plus the floorsheet-native **S4 volume profile** and **S8 broker accumulation/distribution** (concentration capped — suggestive, not a trigger).
- **Confluence scorer (S9):** `score = 100·Σ(wᵢ·voteᵢ·confᵢ)/Σwᵢ` → BUY/SELL/HOLD via config thresholds; retains the full vote vector, top reasons, and **dissents**. Weights are config-driven (`strategy_config`, tunable).
- **Pipeline + persistence:** listens to `IndicatorsComputedEvent`, generates signals per symbol, persists `signal` (action/score/confidence + reasons & votes JSONB, Flyway V7), emits `SignalsGeneratedEvent` — the fan-out point for Phases 6–8. Idempotent (re-run replaces the day).
- **Endpoints:** `/signals/latest`, `/signals`, `/signals/{id}`, `/signals/symbol/{symbol}`, `POST /generate` (admin), `/strategies` (+ analyst PATCH weight/enabled).
- 48/48 non-integration tests green; ArchUnit clean.

### Frontend
- **Signals overview** (latest BUY/SELL/HOLD, action filter), **signal detail** with the **reasons UX** — per-strategy contribution bars + narratives + dissents.
- **Chart signal markers** on the per-symbol page; **clicking a marker opens that day's reasons panel**. **Strategies** page with inline weight/enable tuning (role-enforced by the API).

## Status: Phase 4 — Indicator Engine ✅

A broad, extensible indicator catalog over the candle series.

### Backend (`indicator/` module)
- **Ta4j BarSeries adapter** (DecimalNum canonical path) over candles read through `marketdata.api.CandleSeriesReader`.
- **Built-in catalog** via Ta4j (`BuiltinIndicatorResolver`): SMA/EMA/WMA/RSI/MACD/Bollinger/ATR/ADX/MFI → typed results, **NaN warm-up values dropped**.
- **Custom-study SPI** (§6.5): `CustomIndicator` + `IndicatorDescriptor` (param schema, `OutputKind`, `feedsSignalEngine`) + sealed `IndicatorResult` (`Lines`/`Signals`/`Markers`/`Zones`); auto-discovered by `CustomIndicatorRegistry`. Four shipped studies, **unit-tested**: **HMA** (LINE), **UT Bot** (SIGNAL, recursive ATR stop), **Inside Hammer** (MARKER), **SMC** (ZONE — fractal swings → BOS/CHoCH, FVG, order blocks).
- **Config-composed studies** (Decision E): `/indicators/custom/definitions` registers an allow-listed, range-checked `MA_CROSS` template — validated JSON, never code.
- **Snapshot + events:** listens to `MarketDataReadyEvent`, computes the canonical per-(symbol, date) snapshot (JSONB + promoted RSI/EMA/ATR columns, Flyway V6), emits `IndicatorsComputedEvent`. Pluggable `SeriesCache` (no-op default; Redis drop-in) keyed `(symbol, indicator, paramsHash, lastDate)`.
- **Endpoints:** `/indicators/catalog`, `/indicators` (GET), `/compute`, `/{symbol}/latest`, `/custom`, `PATCH /custom/{id}`, `POST /custom/definitions`. Compute returns a **tagged response** (`outputKind` + variant payload) so the chart dispatches without polymorphic JSON.
- 44/44 non-integration tests green; ArchUnit clean (`indicator` → only `marketdata.api`/`platform.api`).

### Frontend
- Catalog-driven **"Add indicator" dialog** — the settings form is generated from each study's param schema, so new studies appear automatically.
- Chart **overlay rendering by output kind**: LINE/BAND → line series; SIGNAL → line + buy/sell markers; MARKER → pattern markers (v5 `createSeriesMarkers`); ZONE → structure rays + labels (boxes approximated this phase). Active studies shown as removable chips on the per-symbol market page.

## Status: Phase 3 — Market Data & Aggregation ✅

Derives and serves all price/volume structure from the floorsheet.

### Backend (`marketdata/` module)
- **Pure calculators (unit-tested):** `CandleCalculator` (time-ordered OHLC, volume/turnover, VWAP, change%), `VolumeProfileCalculator` (exact POC / 70% Value Area / HVN / LVN, §6.2), `BrokerFlowCalculator` (per-broker net + top-N share + Herfindahl index, §6.3), `IntradayCalculator` (retrospective 1m/5m/15m buckets).
- **Aggregation orchestrator** listens to `TradesIngestedEvent` (`@TransactionalEventListener(AFTER_COMMIT)`): builds per-symbol candle (calendar-aware prev close) + volume profile + broker flow, then the whole-market **`NEPSE` aggregate** (totals, breadth, optional cap-weighted index proxy); emits `MarketDataReadyEvent` for Phase 4.
- **Schema** (Flyway V5): `daily_candle`, `market_aggregate_daily`, `volume_profile` (JSONB bins), `broker_flow_daily`; JDBC upsert DAOs.
- **Cross-module reads** go through a published port — `ingestion.api.FloorsheetReader` — so `marketdata` never touches ingestion internals (ArchUnit-clean).
- **Endpoints:** `/market/candles`, `/vwap`, `/trades`, `/candles/intraday`, `/volume-profile`, `/broker-flow`, `/broker-flow/top`, `/movers`, `/summary`, `/aggregate`.
- **Tests:** 5 calculator tests (no Docker) + an ingest→aggregate integration test (Docker/CI). 40/40 non-integration green; ArchUnit clean.

### Frontend
- **TradingView Lightweight Charts™ v5** price chart (candles + volume overlay, business-day axis, `attributionLogo`) with POC/VAH/VAL drawn as price lines — lazy-loaded, off the initial bundle.
- **Market overview** (breadth + totals + gainers/losers/most-active) and a **per-symbol market page** (chart + broker-flow panel with concentration/HHI). Nav "Market"; instrument detail deep-links to the chart.

## Status: Phase 2 — Ingestion pipeline ✅

The careful part — turning untrusted daily CSV(s) into clean, deduplicated, queryable trades.

### Backend (`ingestion/`)
- **Defensive parser** (`FloorsheetParser`): handles inconsistent whitespace, the comma-after-date timestamp variant (9-column repair), the non-ISO `H:mm:ss:SSS a` time, mixed numerics; validates ranges, the Amount cross-check, and **filename-date vs row-date**; every failure → a `RejectionReason` (one bad row is quarantined, never fails the file). `CsvSanitizer` neutralizes formula injection on the downloadable rejection report (Decision A).
- **Idempotent upsert**: `floorsheet_trade` is monthly-**partitioned** (Flyway V4); bulk JDBC `INSERT … ON CONFLICT (contract_id, trade_date) DO UPDATE`, so re-ingesting a date replaces, never duplicates.
- **Raw archive**: every file stored immutably at `raw/{date}/{hash}.csv` (object store when configured, local dir in dev) — the untrusted filename is never used as a path.
- **Backfill intake** (Decision D): `/uploads`, `/uploads/batch` (caps on file count / size, filename validation, within-batch duplicate-date rejection, **oldest-first** ordering, `202 + batchId`); async pipeline processes dates ascending, each in its own transaction; **crash-recovery runner** resumes incomplete batches on restart; notifications suppressed for all but the latest date.
- **Admin API** (ADMIN-gated): batches/jobs inspection, rejection CSV, retry, reprocess-from-archive; plus an **HMAC-signed webhook** (Decision C, replay window).
- **Tests:** parser golden rows (10), intake caps/ordering, HMAC verify; an integration test ingests + proves idempotency (Docker/CI). Publishes `TradesIngestedEvent` for Phase 3.

### Frontend
- Admin **ingestion console** (ADMIN-gated): validated multi-file upload (client-side `YYYY-MM-DD.csv` check + cap hints), recent-batches list with status, batch detail with per-date row counts, rejection-CSV download, and retry.

## Status: Phase 1 — IAM, Reference & Calendar ✅

On top of the Phase 0 foundation:

### Backend
- **IAM** (`iam/`): register / login / refresh / logout with **JWT access tokens** (HMAC HS256, roles claim) + **rotating refresh tokens** (stored hashed, httpOnly `SameSite` cookie); **Argon2id** password hashing; `USER`/`ANALYST`/`ADMIN` roles; stateless resource-server security with method security and CORS locked to the Angular origin. Endpoints: `/auth/*`, `/users/me`.
- **Reference** (`reference/`): `Instrument`, `Broker`, `TradingDay` entities + Flyway V2; startup seeder (sample scrips/brokers + Sun–Thu calendar); provisional-instrument auto-discovery hook for ingestion. Endpoints: `/instruments`, `/instruments/{symbol}`, `/sectors`, `/brokers`, `/brokers/{id}`.
- **Trading calendar** (`reference.api.TradingCalendar`): holiday-aware `isTradingDay` / `previousTradingDay` / `nTradingDaysBefore` / `tradingDaysBetween` via limit queries. Endpoints: `/calendar/trading-days`, `/calendar/is-open`.
- **Tests (16, no Docker):** calendar look-backs across a holiday/weekend, auth-service rotation, JWT security slice (`jwt()` post-processor), plus the Phase 0 gates. A Testcontainers `AuthFlowIntegrationTest` (register→login→/users/me) runs in CI.

### Frontend
- **Auth**: `AuthService` (login/register/logout, in-memory access token, **silent refresh** via cookie shared across 401s), bootstrap **session restore** (`provideAppInitializer`), `authGuard`/`roleGuard`, login + register pages, shell user-menu/logout.
- **Reference browse**: instruments list (filter + paging via `httpResource`), instrument detail (route input binding), broker registry, trading-calendar viewer. Nav gated on auth state.

See `nepse-signal-platform-implementation-plan.md` for Phases 2–9.

## Status: Phase 0 — Foundations ✅

Phase 0 turns the scaffolds into the real foundation. No business features yet.

### Backend (`backend/`)
- Spring Boot 4.1 on **Java 25** with **virtual threads** enabled.
- Dependencies wired: Spring Batch, Ta4j 0.22.8, Spring Data Redis, MinIO/S3 client, Actuator/Micrometer, Flyway + Postgres, springdoc OpenAPI.
- **Modular-monolith package skeleton** — `reference, ingestion, marketdata, indicator, signal, backtest, charting, screener, watchlist, notification, iam, platform`, each split into `api` / `internal`.
- **`platform` shared kernel** — `ApiError` error model + `@RestControllerAdvice`, `PageResponse`, NPT `NepalClock`, OpenAPI config, object-store config, and `GET /api/v1/system/ping` (the contract handshake endpoint).
- **CI gates that need no Docker:** ArchUnit module-boundary rule + Ta4j Java-25 smoke test + a `@WebMvcTest` for the ping contract.
- **Integration gate:** `@SpringBootTest` boots the full context against a real Postgres via **Testcontainers** (tagged `integration`, excluded from the default `test` run).
- Layered container image (`backend/Dockerfile`).

### Frontend (`frontend/`)
- Angular 22, standalone + **zoneless**, `OnPush`, signals, **Vitest** runner.
- App **shell** (header/nav, live backend-connection badge), design-system tokens (`src/styles.css`, light/dark), shared UI kit (spinner / empty-state / error-state / toast host), lazy-routed `home` + `not-found`.
- **HTTP layer:** `provideHttpClient(withFetch(), …)` with **auth** + **error-normalizing** interceptors; in-memory access-token store (security Decision B); `SystemService` uses `httpResource`.
- **Contract-first codegen:** `npm run gen:api` generates TS DTOs from the backend OpenAPI spec (run via `npx` to avoid the TS-6 peer conflict).

## Running locally

Prerequisites: **JDK 25**, **Node 24+**, and (for the backend) Postgres + Redis — or Docker for the integration tests.

### Backend
```bash
cd backend
./mvnw test                      # unit + arch + ta4j + web-slice (no Docker)
./mvnw test -Dtest.excludedGroups=   # also runs the Testcontainers integration test (needs Docker)
./mvnw spring-boot:run           # starts on :8080 (needs Postgres on localhost:5432, db/user/pass=nepse)
# → http://localhost:8080/api/v1/system/ping   ·   /actuator/health   ·   /swagger-ui.html
```

### Frontend
```bash
cd frontend
npm ci
npm start            # dev server on :4200, proxies /api → :8080 (proxy.conf.json)
npm run build        # production AOT build
npm run test:ci      # Vitest once
npm run gen:api      # regenerate src/app/core/api/generated.ts from the running backend's OpenAPI
```

## CI

`.github/workflows/ci.yml` runs three jobs on every push/PR:
- **backend-unit** — `mvnw test` on JDK 25 (ArchUnit + Ta4j smoke + web slice; no Docker).
- **backend-integration** — `mvnw verify -Dtest.excludedGroups=` (Testcontainers Postgres).
- **frontend** — `npm ci && npm run build && npm run test:ci` on Node 24.

## Conventions
- Module boundaries are enforced at build time by ArchUnit: no module may touch another module's `internal` package.
- Flyway owns the schema; migrations live in `backend/src/main/resources/db/migration` (`V<seq>__<module>_<desc>.sql`).
- Times/trading dates are **NPT (Asia/Kathmandu)** everywhere via `NepalClock`.
- Secrets come from env/secret manager — never committed.

See the implementation plan for Phases 1–9.
