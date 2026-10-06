import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Auth } from '../../core/services/auth';
import { InvoiceReviewService } from './core/services/invoice-review.service';

/**
 * Hosts the ported wholesale invoicing pages inside the FMS layout.
 * A slim tab bar replaces the wholesale app's sidebar — everything below
 * it is the wholesale UI unchanged.
 */
@Component({
  selector: 'app-invoicing-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  template: `
    <nav class="inv-nav">
      <!-- Accountants can create invoices and import supplier bills; catalog
           management and invoice review remain admin-only. -->
      @if (canViewInvoices) {
        <a routerLink="/invoicing/invoices" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
          <mat-icon>receipt_long</mat-icon> Invoices
        </a>
      }

      @if (!isAdmin) {
        <a routerLink="/invoicing/invoices/new" routerLinkActive="active">
          <mat-icon>note_add</mat-icon> New Invoice
        </a>
      }

      <a routerLink="/invoicing/grn" routerLinkActive="active">
        <mat-icon>move_to_inbox</mat-icon> Stock In
      </a>

      @if (canImport && !isAdmin) {
        <a routerLink="/invoicing/import" routerLinkActive="active">
          <mat-icon>upload_file</mat-icon> Import Bills
        </a>
      }

      @if (isAdmin) {
        <a routerLink="/invoicing/items" routerLinkActive="active">
          <mat-icon>inventory_2</mat-icon> Items
        </a>
        <a routerLink="/invoicing/brands" routerLinkActive="active">
          <mat-icon>sell</mat-icon> Brands
        </a>
        <a routerLink="/invoicing/import" routerLinkActive="active">
          <mat-icon>upload_file</mat-icon> Import Bills
        </a>
        <a routerLink="/invoicing/batches" routerLinkActive="active">
          <mat-icon>summarize</mat-icon> Batches
        </a>
        <a routerLink="/invoicing/review" routerLinkActive="active">
          <mat-icon>fact_check</mat-icon> Review
          @if (pendingReview > 0) {
            <span class="badge">{{ pendingReview }}</span>
          }
        </a>
      }
    </nav>
    <router-outlet></router-outlet>
  `,
  styles: [`
    .inv-nav {
      display: flex;
      gap: 4px;
      margin-bottom: 12px;
      border-bottom: 1px solid #e0e6ed;
      flex-wrap: wrap;

      a {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 10px 16px;
        font-size: 13.5px;
        font-weight: 500;
        color: #607d8b;
        text-decoration: none;
        border-bottom: 2px solid transparent;
        margin-bottom: -1px;

        mat-icon { font-size: 18px; width: 18px; height: 18px; }

        &:hover { color: #1565c0; }
        &.active {
          color: #1565c0;
          border-bottom-color: #1565c0;
        }
      }
    }

    .badge {
      min-width: 18px;
      padding: 0 5px;
      border-radius: 9px;
      background: #e53935;
      color: #fff;
      font-size: 11px;
      font-weight: 700;
      line-height: 18px;
      text-align: center;
    }
  `],
})
export class InvoicingShell implements OnInit {
  private auth = inject(Auth);
  private reviewService = inject(InvoiceReviewService);
  private cdr = inject(ChangeDetectorRef);

  pendingReview = 0;

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get canViewInvoices(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }
  get canImport(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.auth.getRole() ?? '');
  }

  ngOnInit(): void {
    if (!this.isAdmin) return;
    this.reviewService.pendingCount().subscribe({
      next: res => { this.pendingReview = res.count; this.cdr.markForCheck(); },
      error: () => {},
    });
  }
}
