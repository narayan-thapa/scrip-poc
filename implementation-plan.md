# NEPSE Signal Platform — Staged Implementation Plan

Derived from `nepse-signal-platform-architecture.md`. Maps the doc's §10 build order
(Steps 1–12) into demonstrable stages, adapted to the **actual scaffold**:

- Backend: Spring Boot **4.1.0**, Java **25**, base package **`np.com.thapanarayan.backend`**
  (doc uses `com.nepse.analytics`; we keep the real package and hang modules under
  `np.com.thapanarayan.backend.<module>`).
- Frontend: Angular **22** scaffold (flat naming, Vitest, signals-first).

## Scaffold deltas to resolve in Stage 0
| Current | Action |
|---|---|
| `spring-boot-starter-amqp` (RabbitMQ) present | **Remove** — doc mandates no broker (Spring Batch + `ApplicationEvent`). |
| H2 only | Keep H2 for unit slices; add **Postgres** runtime + **Testcontainers** for integration. |
| JPA + WebMVC only | Add Batch, Ta4j, ArchUnit, Flyway, Redis, Security, validation, Actuator, WebSocket, springdoc. |
| Single flat package | Create the 11-module package skeleton with `api` / `internal` split. |

---

## Phasing overview

- **Phase A — Foundation** (Stages 0–1): skeleton, module boundaries, reference/calendar.
- **Phase B — Pipeline → Signals** (Stages 2–5): the core value path; first end-to-end demo at the end of Stage 5 (CSV in → BUY/SELL/HOLD out).
- **Phase C — Analysis & UX** (Stages 6–7 + Frontend track): backtesting, charting payload, Angular app.
- **Phase D — Platform features** (Stages 8–9): IAM, watchlists, alerts, realtime notifications.
- **Phase E — Operations** (Stages 10–11): scheduling/orchestration, observability, caching, test hardening.

Stages are mostly sequential because each layer consumes the previous one's output.
Parallelizable tracks are flagged. The Angular track can start after Stage 1 against mocked APIs.

---

## Stage 0 — Platform skeleton & boundaries  *(doc §10.1)*  — ✅ DONE
**Goal:** a buildable monolith with enforced module boundaries and infra wired.
- Fix `pom.xml`: drop AMQP; add `spring-boot-starter-batch`, `org.ta4j:ta4j-core:0.22.1`,
  `archunit-junit5` (test), Flyway (`flyway-core` + `flyway-database-postgresql`),
  `postgresql`, `spring-boot-starter-data-redis`, `spring-boot-starter-security`,
  `spring-boot-starter-validation`, `spring-boot-starter-actuator`,
  `spring-boot-starter-websocket`, `springdoc-openapi`, Testcontainers (test).
- Enable virtual threads (`spring.threads.virtual.enabled=true`).
- Create module packages: `reference, ingestion, marketdata, indicator, signal, backtest,
  charting, watchlist, notification, iam, platform` — each with `api` (published) +
  `internal` (impl).
- `platform` module: `@RestControllerAdvice` error model, paged-response types, NPT clock,
  calendar util interfaces, OpenAPI config.
- **ArchUnit tests**: "no module may reference another module's `internal` package."
- `docker-compose.yml`: Postgres (TimescaleDB image), Redis, MinIO (S3).
- Flyway baseline migration; per-module migration folders by naming convention.

**Done when:** app boots against Postgres, Actuator health is green, ArchUnit + a smoke test pass.

## Stage 1 — Reference & trading calendar  *(doc §10.2)*  — ✅ DONE
**Goal:** master data + calendar-aware date math used by every later stage.
- Entities/repos: `instrument`, `broker`, `trading_day`; seed loaders.
- Calendar service: trading-day look-backs (skip holidays), "previous close", N-trading-days-ago.
- Read APIs: `/instruments`, `/sectors`, `/brokers`, `/calendar/*`.

**Done when:** seeded reference data is queryable; calendar look-back unit tests pass.

## Stage 2 — Ingestion pipeline (the careful part)  *(doc §10.3)*  — ✅ DONE
**Goal:** untrusted CSV(s) → clean, deduped `floorsheet_trade`; safe multi-file backfill.
- Filename contract `^\d{4}-\d{2}-\d{2}\.csv$`; archive under `raw/{date}/{contentHash}.csv`
  (never use raw filename as a path).
- Streaming parser; ordered `DateTimeFormatter` list incl. `H:mm:ss:SSS a` + comma-after-date
  normalization; whitespace normalization.
- Validation: positive qty/price, `|amount − qty*price| ≤ tol`, **filename-date vs row-date
  cross-check**, known/provisional symbol, numeric brokers → quarantine rejects with reason codes.
- Dedup: upsert on `contract_id`.
- **Spring Batch** ingest job: `MultiResourceItemReader`/partitioned step, skip policy →
  `ingestion_rejection`, restartable JobRepository.
- Tables: `ingestion_batch`, `ingestion_job`, `ingestion_rejection`, `floorsheet_trade`
  (monthly partition, `(symbol, trade_date)` index, BRIN on `trade_time`).
- APIs: single + **batch upload** (`202` + `batchId`), trigger, signed webhook (HMAC + replay
  window), batch/job status, rejections report, reprocess. Admin-gated (stub auth until Stage 8).
- `suppressNotifications` flag for historical backfill dates.
- Publish `TradesIngestedEvent`.
- **Security**: treat CSV as hostile — bounded memory, per-file & per-batch caps, formula-injection
  neutralization on any export.
- **Golden-file tests** with the messy sample rows.

**Done when:** sample CSV ingests idempotently; dirty rows quarantine without failing the batch; backfill orders dates ascending.

## Stage 3 — Market data aggregation  *(doc §10.4)*  — ✅ DONE
**Goal:** derive all price/volume structure from trades.
- Daily candle builder (time-ordered OHLC, volume, turnover, VWAP, prev_close, change%).
- **Volume profile** (§6.2: POC/VAH/VAL, HVN/LVN, 70% value area) — custom.
- **Broker flow** (§6.3: net qty/amount, top-N share, HHI) — custom.
- Optional intraday candle bucketing; movers/summary queries.
- Tables: `daily_candle`, `intraday_candle` (partitioned), `volume_profile`, `broker_flow_daily`.
- APIs under `/market/*`. Publish `MarketDataReadyEvent`.

**Done when:** candles/VWAP/profile/broker-flow computed for an ingested date and exposed via API.

## Stage 4 — Indicator engine  *(doc §10.5)*  — ✅ DONE
**Goal:** Ta4j-backed indicator catalog per (symbol, date).
- `candles → Ta4j BarSeries` adapter (`BaseBarSeriesBuilder`, `DecimalNum` canonical path,
  `DoubleNum` for chart overlays).
- `IndicatorService` resolving catalog (RSI/EMA/MACD/Bollinger/ATR/ADX/Supertrend/OBV/MFI/Pivots…).
- Persist `indicator_snapshot` (JSONB + promoted columns); Redis cache for parametrized series
  keyed `(symbol, indicator, paramsHash, lastDate)`.
- APIs: catalog, series, latest, ad-hoc compute. Publish `IndicatorsComputedEvent`.

**Done when:** indicator snapshot persists for a date; ad-hoc compute returns series; cache hits verified.

## Stage 5 — Signal engine  *(doc §10.6)*  ← **first end-to-end milestone**  — ✅ DONE
**Goal:** daily BUY/SELL/HOLD per scrip with structured, human-readable reasons.
- `SignalStrategy` interface + `StrategyVote`/`Reason` records.
- Strategies S1,S2,S3,S5,S6,S7 (Ta4j `Indicator`+`Rule` → graded vote); S4 (volume profile),
  S8 (broker flow) custom.
- **Confluence scorer (S9)**: config-driven weights, score ∈ [−100,+100] → action thresholds;
  expose score as a custom Ta4j `Indicator` (reused by Stage 6).
- Narrative templating; persist `signal` (action, score, votes, reasons) + `strategy_config`.
- APIs: `/signals/latest|/{id}|/symbol/{symbol}`, generate; `/strategies` CRUD.
- Publish `SignalsGeneratedEvent`.

**Done when:** running the pipeline on a date produces signals with breakdown + top reasons. **Demo:** CSV → signals.

## Stage 6 — Backtesting engine  *(doc §10.7)*  — ✅ DONE
**Goal:** validate strategies on history with realistic NEPSE costs, no look-ahead.
- Per-symbol runs via `BarSeriesManager`/`BacktestExecutor` → `TradingRecord`; confluence via
  score-indicator + `Over/UnderIndicatorRule`; **signal-at-close / fill-next-open** shift.
- Custom layer: NEPSE cost model (tiered brokerage, SEBON fee, per-scrip DP, CGT, slippage),
  shared-capital portfolio aggregation.
- Metrics: Ta4j `AnalysisCriterion`s + custom (Sortino, CAGR, exposure, cost-adjusted);
  equity/drawdown curves; trade log with reasons; run versioning + compare.
- Tables `backtest_run|result|trade`; APIs `/backtests/*`.
- Wire `BacktestUpdatedEvent` refresh on `SignalsGeneratedEvent` (BUY list).

**Done when:** a deterministic backtest regression test passes; metrics + curves + trade log returned.

## Stage 7 — Charting API  *(doc §10.8)*  — ✅ DONE
**Goal:** one composite payload for the price chart.
- `/charts/{symbol}`: candles + requested indicator overlays + volume-profile bins + signal markers.
- ETag/caching, read-only composition over Stages 3–5.

**Done when:** composite payload renders a chart with overlays + markers in the frontend.

## Stage 8 — IAM, watchlists, alerts  *(doc §10.10)*  — ✅ DONE (backend)
**Goal:** real auth + user features; replace stubbed admin gating from Stage 2.
- Spring Security 6 + JWT (access/refresh), Argon2id hashing, roles `USER/ANALYST/ADMIN`.
- httpOnly+Secure+SameSite refresh cookie + in-memory access token (doc Decision B option 1);
  CSRF, CORS locked to Angular origin, Redis rate-limiting on auth.
- Watchlist & alert-rule CRUD; tables `app_user, watchlist, watchlist_item, alert_rule`.

**Done when:** login issues tokens; role-gated endpoints enforce; watchlists/alerts CRUD works.
> Note: can be pulled earlier if real auth is needed before exposing admin ingestion publicly.

## Stage 9 — Notifications & realtime  *(doc §10.9)*  — ✅ DONE (backend)
**Goal:** push the day's relevant signals into the web app.
- Alert-rule evaluator on `SignalsGeneratedEvent` → persist `notification` (sent flag as outbox).
- STOMP WebSocket `/ws` (`/topic/signals`, `/user/queue/notifications`, `/topic/pipeline-status`)
  + SSE fallback `/notifications/stream`; dispatcher pushes unsent rows; dedup per (user, signal).
- Redis backplane if multi-instance.

**Done when:** completing a daily run pushes a toast + persists a notification feed entry.

## Stage 10 — Orchestration & scheduling  *(doc §10.11)*  — ✅ DONE
**Goal:** the durable daily cycle.
- `@Scheduled` 15:01 NPT trigger (+ ShedLock if replicated) launching the Batch job;
  admin manual trigger + reprocess; `/system/pipeline/status` reads Batch step-execution state.
- Confirm restart-from-failed-step via JobRepository.

**Done when:** scheduled trigger runs the full pipeline; killing mid-run and restarting resumes from the failed step.

## Stage 11 — Observability, caching, test hardening  *(doc §10.12)*  — ✅ DONE
**Goal:** production-readiness.
- Micrometer metrics + tracing; structured logging with per-run correlation id.
- Redis caching with explicit eviction on recompute.
- Test suite: ArchUnit, per-module `@SpringBootTest` slices, Testcontainers (PG/Redis),
  `@SpringBatchTest` (incl. restart), golden-file ingestion, deterministic backtest regression.

**Done when:** coverage targets met; dashboards/health expose pipeline status.

---

## Frontend track (Angular 22) — parallel from Stage 1  *(doc §12)*
- Signals-first (`OnPush` default, Signal Forms, `httpResource`/`rxResource`, Fetch HttpClient).
- Screens: signal list/detail (reasons UX), charting (Lightweight Charts + volume profile),
  backtest runner/results, watchlist, notification center + toasts, admin ingestion console.
- Realtime via STOMP + SSE fallback.
- Build incrementally behind each backend stage; mock APIs until the matching stage lands.

**Phase C progress (done):** shell + routing (zoneless, `provideHttpClient(withFetch)`, dev proxy
to `:8080`), signals list/detail with vote+reason breakdown, composite charting view (inline SVG
price line + signal markers + volume-profile summary), and backtest runner (metrics grid + equity
curve + trade ledger). All signals-first/`OnPush`, `httpResource`-driven, lazy-loaded.
**Phase D progress (done):** auth (login/register, in-memory access token + httpOnly-cookie
refresh, HTTP interceptor with silent refresh-and-retry, route guards, session restore on load),
watchlist CRUD screen, notification feed + unread badge + realtime toasts (dependency-free
fetch-based SSE with Bearer auth). **Still deferred:** admin ingestion console; STOMP client (SSE
used instead to avoid a new dependency).

## Cross-cutting (every stage)  *(doc §11)*
HTTPS only; CORS to Angular origin; per-user/IP rate limits; secrets from env/secret manager
(never in code/config/logs/tests); least-privilege DB role; validate & bound every query param;
treat all external input as hostile.

## Suggested first execution slice
Stage 0 → Stage 1 → a thin vertical of Stage 2 (single-file upload + parse + persist) so you
can ingest the sample CSV and inspect `floorsheet_trade` before building aggregation.
