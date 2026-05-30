import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Auth } from '../../core/services/auth';
import { SummaryLoadBillComponent } from './summary-load-bill/summary-load-bill';
import { StockItemEntryComponent } from './stock-item-entry/stock-item-entry';
import { EndOfMonthLinkingComponent } from './end-of-month-linking/end-of-month-linking';
import { StockReductionStatusComponent } from './stock-reduction-status/stock-reduction-status';
import { InventoryTabComponent } from './inventory/inventory-tab';

@Component({
  selector: 'app-stock-tab',
  standalone: true,
  imports: [
    CommonModule,
    MatTabsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    SummaryLoadBillComponent,
    StockItemEntryComponent,
    EndOfMonthLinkingComponent,
    StockReductionStatusComponent,
    InventoryTabComponent,
  ],
  templateUrl: './stock-tab.html',
  styleUrl: './stock-tab.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockTab implements OnInit {
  get isAdmin(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }

  get canViewStock(): boolean {
    return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT', 'OWNER'].includes(
      this.auth.getRole() ?? ''
    );
  }

  get isReadOnly(): boolean {
    return this.auth.getRole() === 'OWNER';
  }

  constructor(private auth: Auth) {}

  ngOnInit(): void {
    if (!this.canViewStock) {
      throw new Error('Unauthorized access to stock tab');
    }
  }
}
