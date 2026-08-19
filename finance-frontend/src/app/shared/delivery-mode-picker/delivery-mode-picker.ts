import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output,
} from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DeliveryService, DeliveryRun } from '../../core/services/delivery';
import { localDateStr } from '../../core/utils/date-utils';

export type DeliveryMode = 'ROUTE' | 'IMMEDIATE' | 'STORE_PICKUP' | 'UNSPECIFIED';

/** What the parent needs to send with whatever it is saving. */
export interface DeliveryChoice {
  deliveryMode: DeliveryMode;
  deliveryRunId?: number;
}

/**
 * How the goods go out, decided once and reused.
 *
 * <p>The open round answers it by default. That is the whole point of a sticky run —
 * route work is entered in a hurry, and asking per bill would be asking the same
 * question forty times about one lorry. The chips are there for the exception.
 *
 * <p>With no round open there is nothing to infer from, so an answer is required: a
 * bill left unsaid lands UNSPECIFIED, outside every delivery figure, and nothing
 * reports the ones nobody classified.
 */
@Component({
  selector: 'app-delivery-mode-picker',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './delivery-mode-picker.html',
  styleUrl: './delivery-mode-picker.scss',
})
export class DeliveryModePicker implements OnInit {

  /** What this picker is deciding for, so the wording fits the screen it is on. */
  @Input() subject = 'this bill';

  /** Emitted whenever the choice changes, and once when the run has loaded. */
  @Output() choice = new EventEmitter<DeliveryChoice>();

  /** Emitted alongside, so the parent can block its own save. */
  @Output() incomplete = new EventEmitter<boolean>();

  currentRun: DeliveryRun | null = null;
  loading = true;
  /** Set only when overriding the round for this one entry. */
  override: DeliveryMode | null = null;

  constructor(private delivery: DeliveryService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void {
    this.delivery.current().subscribe({
      next: (run) => {
        this.currentRun = run;
        this.loading = false;
        this.emit();
        this.cdr.markForCheck();
      },
      // A failed lookup must not be read as "no run open" and quietly turn route work
      // into store pickups, so it resolves to nothing chosen and the picker asks.
      error: () => { this.loading = false; this.emit(); this.cdr.markForCheck(); },
    });
  }

  get mode(): DeliveryMode {
    if (this.override) return this.override;
    return this.currentRun ? 'ROUTE' : 'UNSPECIFIED';
  }

  /** True when the open round's date has passed — yesterday's lorry, still open. */
  get runIsStale(): boolean {
    return !!this.currentRun && this.currentRun.plannedDate < localDateStr();
  }

  get required(): boolean { return !this.currentRun; }
  get missing(): boolean  { return this.required && !this.override; }

  set(mode: DeliveryMode | null): void {
    this.override = mode;
    this.emit();
    this.cdr.markForCheck();
  }

  private emit(): void {
    const mode = this.mode;
    this.choice.emit({
      deliveryMode: mode,
      deliveryRunId: mode === 'ROUTE' && this.currentRun ? this.currentRun.id : undefined,
    });
    this.incomplete.emit(this.missing);
  }
}
