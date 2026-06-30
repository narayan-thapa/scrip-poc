import type { CanvasRenderingTarget2D } from 'fancy-canvas';
import {
  IChartApiBase,
  IPrimitivePaneRenderer,
  IPrimitivePaneView,
  ISeriesApi,
  ISeriesPrimitive,
  PrimitivePaneViewZOrder,
  SeriesAttachedParameter,
  SeriesType,
  Time,
} from 'lightweight-charts';

/** A resolved SMC zone ready to draw: a price band from a start time projected forward. */
export interface SmcZoneInput {
  fromTime: Time;
  top: number;
  bottom: number;
  bullish: boolean;
  mitigated: boolean;
}

/** RGB triples matching the app palette (green bullish / red bearish). */
const BULL_RGB = '63, 185, 80';
const BEAR_RGB = '248, 81, 73';

/**
 * Draws each zone as a filled rectangle spanning [bottom, top] in price, from the
 * zone's origin bar projected to the chart's right edge — the conventional "zone
 * holds until revisited" look. Mitigated zones are fainter and dashed. Coordinates
 * are read live from the series/time scale each frame, so zooming and panning track.
 */
class SmcZonesRenderer implements IPrimitivePaneRenderer {
  constructor(
    private readonly zones: SmcZoneInput[],
    private readonly chart: IChartApiBase<Time>,
    private readonly series: ISeriesApi<SeriesType, Time>,
  ) {}

  draw(target: CanvasRenderingTarget2D): void {
    const timeScale = this.chart.timeScale();
    const rightEdge = timeScale.width();

    target.useMediaCoordinateSpace((scope) => {
      const ctx = scope.context;
      for (const zone of this.zones) {
        const x = timeScale.timeToCoordinate(zone.fromTime);
        const yTop = this.series.priceToCoordinate(zone.top);
        const yBottom = this.series.priceToCoordinate(zone.bottom);
        if (x === null || yTop === null || yBottom === null) {
          continue;
        }
        const xStart = Math.max(0, x);
        const width = Math.max(2, rightEdge - xStart);
        const height = Math.max(1, yBottom - yTop);
        const rgb = zone.bullish ? BULL_RGB : BEAR_RGB;

        ctx.fillStyle = `rgba(${rgb}, ${zone.mitigated ? 0.06 : 0.16})`;
        ctx.fillRect(xStart, yTop, width, height);

        ctx.strokeStyle = `rgba(${rgb}, 0.5)`;
        ctx.lineWidth = 1;
        ctx.setLineDash(zone.mitigated ? [3, 3] : []);
        ctx.strokeRect(xStart, yTop, width, height);
      }
      ctx.setLineDash([]);
    });
  }
}

class SmcZonesPaneView implements IPrimitivePaneView {
  constructor(private readonly source: SmcZonesPrimitive) {}

  // Behind the candles so wicks/bodies stay readable.
  zOrder(): PrimitivePaneViewZOrder {
    return 'bottom';
  }

  renderer(): IPrimitivePaneRenderer | null {
    const { chart, series } = this.source;
    if (!chart || !series) {
      return null;
    }
    return new SmcZonesRenderer(this.source.zones, chart, series);
  }
}

/**
 * Series primitive that overlays SMC order-block / fair-value-gap zones on the price
 * pane. Attach once with {@link ISeriesApi.attachPrimitive}; feed zones via
 * {@link setZones}.
 */
export class SmcZonesPrimitive implements ISeriesPrimitive<Time> {
  zones: SmcZoneInput[] = [];
  chart: IChartApiBase<Time> | null = null;
  series: ISeriesApi<SeriesType, Time> | null = null;

  private readonly views: readonly IPrimitivePaneView[] = [new SmcZonesPaneView(this)];
  private requestUpdate?: () => void;

  attached(param: SeriesAttachedParameter<Time>): void {
    this.chart = param.chart;
    this.series = param.series;
    this.requestUpdate = param.requestUpdate;
  }

  detached(): void {
    this.chart = null;
    this.series = null;
    this.requestUpdate = undefined;
  }

  paneViews(): readonly IPrimitivePaneView[] {
    return this.views;
  }

  setZones(zones: SmcZoneInput[]): void {
    this.zones = zones;
    this.requestUpdate?.();
  }
}
