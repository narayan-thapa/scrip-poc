import { httpResource } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, output, signal } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { IndicatorDescriptor, IndicatorOutputKind } from '../../../core/api/models';

export interface ActiveStudy {
  key: string;
  id: string;
  name: string;
  outputKind: IndicatorOutputKind;
  params: Record<string, unknown>;
  color: string;
}

const PALETTE = ['#2563eb', '#9333ea', '#d97706', '#0891b2', '#db2777', '#65a30d'];

/** Generic "Add indicator" form built entirely from the catalog's param schema. */
@Component({
  selector: 'app-add-indicator',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="add-indicator">
      <select [value]="selectedId()" (change)="selectedId.set($any($event.target).value)">
        <option value="">Add indicator…</option>
        @for (d of catalog.value() ?? []; track d.id) {
          <option [value]="d.id">{{ d.name }} ({{ d.category }})</option>
        }
      </select>

      @if (selected(); as d) {
        <div class="params">
          @for (p of d.params; track p.name) {
            <label>
              {{ p.name }}
              @if (p.type === 'BOOLEAN') {
                <input type="checkbox" [checked]="!!params()[p.name]" (change)="set(p.name, $any($event.target).checked)" />
              } @else {
                <input
                  type="number"
                  [value]="$any(params()[p.name])"
                  [min]="$any(p.min)"
                  [max]="$any(p.max)"
                  (change)="set(p.name, +$any($event.target).value)"
                />
              }
            </label>
          }
          <button type="button" (click)="add()">Add</button>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .add-indicator { display: flex; gap: var(--space-3); align-items: flex-end; flex-wrap: wrap; }
      select, input { padding: var(--space-1) var(--space-2); border: 1px solid var(--color-border);
        border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: inherit; }
      .params { display: flex; gap: var(--space-3); align-items: flex-end; flex-wrap: wrap; }
      label { display: flex; flex-direction: column; font-size: 0.75rem; color: var(--color-text-muted); gap: 0.15rem; }
      button { background: var(--color-accent); color: #fff; border: 0; padding: var(--space-2) var(--space-3); }
    `,
  ],
})
export class AddIndicator {
  readonly added = output<ActiveStudy>();

  private addCount = 0;
  protected readonly selectedId = signal('');
  protected readonly params = signal<Record<string, unknown>>({});

  protected readonly catalog = httpResource<IndicatorDescriptor[]>(
    () => `${environment.apiBaseUrl}/api/v1/indicators/catalog`,
  );

  protected readonly selected = computed<IndicatorDescriptor | undefined>(() =>
    (this.catalog.value() ?? []).find((d) => d.id === this.selectedId()),
  );

  constructor() {
    // Reset params to the descriptor defaults whenever the selected study changes.
    effect(() => {
      const d = this.selected();
      const next: Record<string, unknown> = {};
      d?.params.forEach((p) => (next[p.name] = p.defaultValue));
      this.params.set(next);
    });
  }

  set(name: string, value: unknown): void {
    this.params.update((p) => ({ ...p, [name]: value }));
  }

  add(): void {
    const d = this.selected();
    if (!d) {
      return;
    }
    this.added.emit({
      key: `${d.id}-${this.addCount++}`,
      id: d.id,
      name: d.name,
      outputKind: d.outputKind,
      params: { ...this.params() },
      color: PALETTE[this.addCount % PALETTE.length],
    });
    this.selectedId.set('');
  }
}
