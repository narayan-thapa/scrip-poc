// TypeScript mirrors of the backend published API views. Numbers map Java
// BigDecimal/double (Jackson serializes both as JSON numbers); dates are ISO strings.

export type SignalAction = 'BUY' | 'SELL' | 'HOLD';

export interface ReasonView {
  strategyId: string;
  indicator: string;
  condition: string;
  observedValue: string;
  threshold: string;
  contribution: number;
  narrative: string;
}

export interface StrategyVoteView {
  strategyId: string;
  label: string;
  vote: number;
  confidence: number;
  weight: number;
  contribution: number;
  applicable: boolean;
  reasons: ReasonView[];
}

export interface SignalView {
  id: string;
  symbol: string;
  tradeDate: string;
  action: SignalAction;
  score: number;
  barCount: number;
  votes: StrategyVoteView[];
  topReasons: ReasonView[];
  narrative: string;
  computedAt: string;
}

export interface StrategyConfigView {
  strategyId: string;
  label: string;
  enabled: boolean;
  weight: number;
  updatedAt: string;
}

export interface DailyCandleView {
  symbol: string;
  tradeDate: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  turnover: number;
  vwap: number;
  previousClose: number | null;
  changePercent: number | null;
  tradeCount: number;
}

export interface IndicatorPoint {
  date: string;
  value: number;
}

export interface IndicatorSeriesView {
  symbol: string;
  indicator: string;
  params: number[];
  lines: Record<string, IndicatorPoint[]>;
}

export interface VolumeProfileView {
  symbol: string;
  tradeDate: string;
  poc: number;
  valueAreaHigh: number;
  valueAreaLow: number;
  priceMin: number;
  priceMax: number;
}

export interface SignalMarkerView {
  date: string;
  action: SignalAction;
  score: number;
  signalId: string;
  narrative: string;
}

export interface ChartView {
  symbol: string;
  from: string;
  to: string;
  candles: DailyCandleView[];
  indicators: IndicatorSeriesView[];
  volumeProfile: VolumeProfileView | null;
  signals: SignalMarkerView[];
}

export type BacktestStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface BacktestMetricsView {
  totalReturnPct: number;
  cagrPct: number;
  annualizedVolatilityPct: number;
  sharpe: number;
  sortino: number;
  maxDrawdownPct: number;
  maxDrawdownDays: number;
  winRatePct: number;
  profitFactor: number;
  avgWinPct: number;
  avgLossPct: number;
  expectancy: number;
  exposurePct: number;
  tradeCount: number;
  totalCosts: number;
  startingCapital: number;
  finalEquity: number;
}

export interface BacktestRunView {
  id: string;
  strategyLabel: string;
  symbols: string[];
  dateFrom: string;
  dateTo: string;
  startingCapital: number;
  status: BacktestStatus;
  metrics: BacktestMetricsView | null;
  errorMessage: string | null;
  createdAt: string;
}

export interface EquityPointView {
  date: string;
  equity: number;
  drawdownPct: number;
}

export interface BacktestTradeView {
  symbol: string;
  entryDate: string;
  entryPrice: number;
  exitDate: string | null;
  exitPrice: number | null;
  quantity: number;
  costs: number;
  pnl: number;
  returnPct: number;
  entryReason: string;
  exitReason: string;
}

export interface BacktestRequest {
  symbols: string[];
  from: string;
  to: string;
  startingCapital?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type Role = 'USER' | 'ANALYST' | 'ADMIN';

export interface UserView {
  id: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserView;
}

export interface WatchlistItemView {
  symbol: string;
  addedAt: string;
}

export interface WatchlistView {
  id: string;
  name: string;
  items: WatchlistItemView[];
  createdAt: string;
}

export interface NotificationView {
  id: string;
  signalId: string | null;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}
