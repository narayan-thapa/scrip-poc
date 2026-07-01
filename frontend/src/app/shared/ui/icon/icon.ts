import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Icon name — one of the curated stroke glyphs below. */
export type IconName =
  | 'home'
  | 'dashboard'
  | 'market'
  | 'price-drop'
  | 'signals'
  | 'strategies'
  | 'backtests'
  | 'watchlists'
  | 'alerts'
  | 'bell'
  | 'instruments'
  | 'brokers'
  | 'calendar'
  | 'pipeline'
  | 'ingestion'
  | 'menu'
  | 'logout'
  | 'chevron-left';

/**
 * Small inline SVG icon set (Lucide-style, 24×24, stroke = currentColor). Kept as a typed
 * {@code @switch} so icons stay tree-shakeable and type-checked — no external icon-font dependency.
 */
@Component({
  selector: 'app-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      xmlns="http://www.w3.org/2000/svg"
      [attr.width]="size()"
      [attr.height]="size()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @switch (name()) {
        @case ('home') {
          <path d="M3 9.5 12 3l9 6.5" /><path d="M5 10v10h14V10" />
        }
        @case ('dashboard') {
          <rect x="3" y="3" width="7" height="9" rx="1" /><rect x="14" y="3" width="7" height="5" rx="1" />
          <rect x="14" y="12" width="7" height="9" rx="1" /><rect x="3" y="16" width="7" height="5" rx="1" />
        }
        @case ('market') {
          <path d="M3 3v18h18" /><path d="m7 14 3-4 3 3 5-7" />
        }
        @case ('price-drop') {
          <path d="M3 4v16h18" /><path d="m7 8 4 5 3-2 4 6" /><path d="M18 17v-4h-4" />
        }
        @case ('signals') {
          <path d="M2 12h4l3 8 4-16 3 8h6" />
        }
        @case ('strategies') {
          <path d="m12 2 9 5-9 5-9-5 9-5Z" /><path d="m3 12 9 5 9-5" /><path d="m3 17 9 5 9-5" />
        }
        @case ('backtests') {
          <path d="M3 12a9 9 0 1 0 3-6.7L3 8" /><path d="M3 3v5h5" /><path d="M12 8v4l3 2" />
        }
        @case ('watchlists') {
          <path d="m12 3 2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 18l-5.8 3 1.1-6.5L2.6 9.8l6.5-.9L12 3Z" />
        }
        @case ('alerts') {
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
          <path d="m21 5-2-2M3 5l2-2" />
        }
        @case ('bell') {
          <path d="M18 8a6 6 0 1 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" /><path d="M13.7 21a2 2 0 0 1-3.4 0" />
        }
        @case ('instruments') {
          <path d="M8 6h13M8 12h13M8 18h13" /><path d="M3 6h.01M3 12h.01M3 18h.01" />
        }
        @case ('brokers') {
          <path d="M3 21h18" /><path d="M5 21V7l7-4 7 4v14" /><path d="M9 9h.01M15 9h.01M9 13h.01M15 13h.01" />
        }
        @case ('calendar') {
          <rect x="3" y="4" width="18" height="17" rx="2" /><path d="M3 9h18M8 2v4M16 2v4" />
        }
        @case ('pipeline') {
          <circle cx="5" cy="6" r="2" /><circle cx="5" cy="18" r="2" /><circle cx="19" cy="12" r="2" />
          <path d="M5 8v8M7 6h6a4 4 0 0 1 4 4v0M7 18h6a4 4 0 0 0 4-4v0" />
        }
        @case ('ingestion') {
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><path d="M7 10l5 5 5-5" /><path d="M12 15V3" />
        }
        @case ('menu') {
          <path d="M4 6h16M4 12h16M4 18h16" />
        }
        @case ('logout') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" /><path d="m16 17 5-5-5-5" /><path d="M21 12H9" />
        }
        @case ('chevron-left') {
          <path d="m15 18-6-6 6-6" />
        }
      }
    </svg>
  `,
  styles: [`:host { display: inline-flex; line-height: 0; }`],
})
export class Icon {
  readonly name = input.required<IconName>();
  readonly size = input(20);
}
