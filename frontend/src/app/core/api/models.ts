/**
 * Hand-written API models for Phase 0. From Phase 1 onward, DTOs are generated from the backend
 * OpenAPI spec into `generated.ts` (see `npm run gen:api`); these mirror the platform's published
 * contract so the app compiles before codegen is wired into CI.
 */

/** Canonical error envelope returned by every backend endpoint on failure (platform ApiError). */
export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors: { field: string; message: string }[];
}

/** Response of `GET /api/v1/system/ping` — the Phase 0 contract handshake. */
export interface PingResponse {
  service: string;
  status: string;
  time: string;
  zone: string;
}

/** Stable page envelope returned by every list endpoint (platform PageResponse). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---- IAM ----

export type Role = 'USER' | 'ANALYST' | 'ADMIN';

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserProfile {
  id: string;
  email: string;
  role: Role;
}

// ---- Reference ----

export interface InstrumentDto {
  symbol: string;
  name: string;
  sector: string | null;
  type: 'EQUITY' | 'INDEX';
  listedShares: number | null;
  status: 'ACTIVE' | 'SUSPENDED' | 'DELISTED';
  priceBand: number | null;
  provisional: boolean;
}

export interface BrokerDto {
  brokerId: number;
  name: string;
  status: string;
}

export interface IsOpenResponse {
  date: string;
  open: boolean;
}

// ---- Ingestion (admin) ----

export interface IntakeResult {
  batchId: string;
  files: { filename: string; tradeDate: string; bytes: number }[];
}

export type IngestionStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'PARTIAL';

export interface BatchSummary {
  id: string;
  status: IngestionStatus;
  fileCount: number;
  dateFrom: string | null;
  dateTo: string | null;
  submittedAt: string;
  finishedAt: string | null;
}

export interface JobSummary {
  id: string;
  tradeDate: string;
  sourceFilename: string;
  status: IngestionStatus;
  rowsRead: number;
  rowsAccepted: number;
  rowsRejected: number;
  rowsDuplicate: number;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface BatchDetail {
  batch: BatchSummary;
  jobs: JobSummary[];
}

// ---- Market data ----

export interface CandleDto {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  turnover: number;
  trades: number;
  vwap: number;
  changePct: number | null;
}

export interface VolumeProfileDto {
  symbol: string;
  windowFrom: string;
  windowTo: string;
  poc: number;
  vah: number;
  val: number;
  bins: { price: number; volume: number }[];
}

export interface BrokerFlowRow {
  brokerId: number;
  buyQty: number;
  sellQty: number;
  netQty: number;
  buyAmount: number;
  sellAmount: number;
}

export interface BrokerFlowDto {
  symbol: string;
  date: string;
  brokers: BrokerFlowRow[];
  topBuyerShare: number;
  topSellerShare: number;
  hhiBuy: number;
  hhiSell: number;
}

export interface Mover {
  symbol: string;
  close: number;
  changePct: number | null;
  volume: number;
  turnover: number;
}

export interface MoversDto {
  date: string;
  gainers: Mover[];
  losers: Mover[];
  mostActiveByVolume: Mover[];
  mostActiveByTurnover: Mover[];
}

export interface SummaryDto {
  date: string;
  advances: number;
  declines: number;
  unchanged: number;
  totalVolume: number;
  totalTurnover: number;
  totalTrades: number;
}

// ---- Indicators ----

export type IndicatorOutputKind = 'LINE' | 'BAND' | 'SIGNAL' | 'MARKER' | 'ZONE';

export interface ParamSpec {
  name: string;
  type: 'INT' | 'DOUBLE' | 'BOOLEAN' | 'STRING' | 'ENUM';
  defaultValue: unknown;
  min: number | null;
  max: number | null;
  options: string[];
}

export interface IndicatorDescriptor {
  id: string;
  name: string;
  category: string;
  outputKind: IndicatorOutputKind;
  params: ParamSpec[];
  feedsSignalEngine: boolean;
}

export interface IndicatorPoint {
  time: string;
  value: number;
}

export interface IndicatorEvent {
  time: string;
  side: 'BUY' | 'SELL';
  price: number;
}

export interface IndicatorMarker {
  time: string;
  position: string;
  shape: string;
  color: string;
  text: string;
}

export interface IndicatorZones {
  boxes: { fromTime: string; toTime: string; top: number; bottom: number; color: string; label: string }[];
  rays: { time: string; price: number; color: string; label: string }[];
  labels: { time: string; price: number; text: string }[];
}

export interface ComputeResponse {
  id: string;
  outputKind: IndicatorOutputKind;
  lines: Record<string, IndicatorPoint[]> | null;
  plot: IndicatorPoint[] | null;
  events: IndicatorEvent[] | null;
  markers: IndicatorMarker[] | null;
  zones: IndicatorZones | null;
}

// ---- Signals ----

export type SignalActionType = 'BUY' | 'SELL' | 'HOLD';

export interface SignalSummary {
  id: string;
  symbol: string;
  tradeDate: string;
  action: SignalActionType;
  score: number;
  confidence: number;
}

export interface ReasonDto {
  strategyId: string;
  indicator: string;
  condition: string;
  observedValue: string;
  threshold: string;
  contribution: number;
  narrative: string;
}

export interface VoteEntry {
  strategyId: string;
  name: string;
  vote: number;
  confidence: number;
  weight: number;
  contribution: number;
}

export interface SignalDetail extends SignalSummary {
  reasons: ReasonDto[];
  votes: VoteEntry[];
}

export interface StrategyConfig {
  id: string;
  name: string;
  type: string;
  weight: number;
  enabled: boolean;
}

// ---- Backtests ----

export interface BacktestRunView {
  id: string;
  symbol: string;
  from: string;
  to: string;
  startingCapital: number;
  buyThreshold: number;
  sellThreshold: number;
  status: string;
  createdAt: string;
}

export type BacktestMetrics = Record<string, number>;

export interface RunResponse {
  runId: string;
  metrics: BacktestMetrics;
}

export interface RunDetail {
  run: BacktestRunView;
  metrics: BacktestMetrics;
}

export interface EquityPoint {
  date: string;
  equity: number;
  drawdownPct: number;
}

export interface BacktestTrade {
  entryDate: string;
  entryPrice: number;
  exitDate: string;
  exitPrice: number;
  quantity: number;
  costs: number;
  pnl: number;
  returnPct: number;
  entryReason: string;
  exitReason: string;
}

// ---- Screeners / dashboard ----

export interface SignalTag {
  action: SignalActionType;
  score: number | null;
}

export interface ActiveRow {
  symbol: string;
  close: number;
  changePct: number | null;
  volume: number;
  turnover: number;
  tradesCount: number;
  signal: SignalTag | null;
}

export interface RvolRow {
  symbol: string;
  volume: number;
  rvolRatio: number;
  rvolZ: number;
  changePct: number | null;
  signal: SignalTag | null;
}

export interface PriceDropRow {
  symbol: string;
  close: number;
  closeNAgo: number;
  pctChange: number;
  drawdownFromHigh: number;
  sharpness: number;
  windowHigh: number;
  windowLow: number;
  volume: number;
  rvolRatio: number;
  insufficientHistory: boolean;
  signal: SignalTag | null;
}

export interface BreadthDto {
  advances: number;
  declines: number;
  unchanged: number;
  totalVolume: number;
  totalTurnover: number;
  totalTrades: number;
}

export interface DashboardDto {
  date: string;
  breadth: BreadthDto;
  highTrade: ActiveRow[];
  lowTrade: ActiveRow[];
  rvolSpikes: RvolRow[];
  rvolDrops: RvolRow[];
}

// ---- Notifications / watchlists / alerts ----

export interface NotificationItem {
  id: string;
  signalId: string | null;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

export interface WatchlistDto {
  id: string;
  name: string;
  symbols: string[];
}

export interface AlertRuleDto {
  id: string;
  type: string;
  params: Record<string, unknown>;
  enabled: boolean;
}

// ---- Pipeline status ----

export interface StageStatus {
  stage: string;
  lastDate: string | null;
  updatedAt: string | null;
}

// ---- Composite chart payload (F7) ----

export interface ChartCandle {
  time: string;
  open: number;
  high: number;
  low: number;
  close: number;
}

export interface ChartVolumePoint {
  time: string;
  value: number;
  color: string;
}

export interface ChartVolumeProfile {
  poc: number;
  vah: number;
  val: number;
  bins: { price: number; volume: number }[];
}

export interface ChartPayload {
  symbol: string;
  from: string;
  to: string;
  candles: ChartCandle[];
  volume: ChartVolumePoint[];
  volumeProfile: ChartVolumeProfile | null;
  markers: { id: string; time: string; action: string; score: number }[];
}
