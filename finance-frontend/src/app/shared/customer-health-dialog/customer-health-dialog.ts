import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  BusinessHealth,
  CustomerHealth,
  CustomerHealthService,
  HealthRating,
} from '../../core/services/customer-health';

export interface CustomerHealthDialogData {
  customerId: number;
  customerName: string;
}

/**
 * A customer's payment record, so the question "should we sell to them again?" has an
 * answer that is not somebody's recollection.
 *
 * <p>Shown per business, because a shop can be reliable on stationery and slow on
 * Rainco. The reasons under each rating are as important as the rating: whoever reads
 * this is about to act on it or overrule it, and either way they need to see what it saw.
 */
@Component({
  selector: 'app-customer-health-dialog',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './customer-health-dialog.html',
  styleUrl: './customer-health-dialog.scss',
})
export class CustomerHealthDialog implements OnInit {
  health: CustomerHealth | null = null;
  loading = true;
  error = '';

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: CustomerHealthDialogData,
    private ref: MatDialogRef<CustomerHealthDialog>,
    private service: CustomerHealthService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.service.getForCustomer(this.data.customerId).subscribe({
      next: (h) => {
        this.health = h;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load this customer’s record.';
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  close(): void { this.ref.close(); }

  ratingLabel(r: HealthRating): string {
    switch (r) {
      case 'GOOD':    return 'Good';
      case 'WATCH':   return 'Watch';
      case 'CAREFUL': return 'Careful';
      default:        return r;
    }
  }

  ratingIcon(r: HealthRating): string {
    switch (r) {
      case 'GOOD':    return 'verified';
      case 'WATCH':   return 'schedule';
      case 'CAREFUL': return 'warning';
      default:        return 'help';
    }
  }

  /**
   * Which way the days-to-settle is moving. Said as a word rather than an arrow so it
   * survives being read quickly on a phone in a store room.
   */
  trend(b: BusinessHealth): 'worse' | 'better' | 'steady' | null {
    if (b.avgDaysToSettleRecent == null || b.avgDaysToSettle == null) return null;
    const diff = b.avgDaysToSettleRecent - b.avgDaysToSettle;
    if (diff >= 15) return 'worse';
    if (diff <= -15) return 'better';
    return 'steady';
  }

  /** The figure to lead with: how they have been lately, falling back to all time. */
  headlineDays(b: BusinessHealth): number | null {
    return b.avgDaysToSettleRecent ?? b.avgDaysToSettle ?? null;
  }
}
