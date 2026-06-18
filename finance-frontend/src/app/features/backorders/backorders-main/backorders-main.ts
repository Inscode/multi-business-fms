import { Component, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { SubmitBackorder } from '../submit-backorder/submit-backorder';
import { ReviewBackorders } from '../review-backorders/review-backorders';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-backorders-main',
  standalone: true,
  imports: [CommonModule, MatTabsModule, MatIconModule, SubmitBackorder, ReviewBackorders],
  template: `
    <div class="main-page">
      <h2 class="page-title">Backorders</h2>
      <mat-tab-group animationDuration="150ms">
        <mat-tab *ngIf="canSubmit">
          <ng-template mat-tab-label><mat-icon>add_shopping_cart</mat-icon>&nbsp;Submit Backorder</ng-template>
          <div class="tab-body"><app-submit-backorder /></div>
        </mat-tab>
        <mat-tab>
          <ng-template mat-tab-label><mat-icon>pending_actions</mat-icon>&nbsp;Review Backorders</ng-template>
          <div class="tab-body"><app-review-backorders [readonly]="isReadOnly" /></div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .main-page { max-width: 1100px; }
    .page-title { font-size: 20px; font-weight: 600; margin: 0 0 16px; color: #1a1a2e; }
    .tab-body { padding: 16px 0; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BackordersMain {
  constructor(private auth: Auth) {}

  get role(): string { return this.auth.getRole() ?? ''; }
  get canSubmit(): boolean { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.role); }
  get isReadOnly(): boolean { return !['ADMIN'].includes(this.role); }
}
