import { Component, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { TabPurchaseEntry } from '../tab-purchase-entry/tab-purchase-entry';
import { AdvanceBonusEntry } from '../advance-bonus-entry/advance-bonus-entry';
import { WorkerFinanceOwnerReview } from '../owner-review/owner-review';
import { WorkerFinanceAdminConfirm } from '../admin-confirm/admin-confirm';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-worker-finance-main',
  standalone: true,
  imports: [
    CommonModule, MatTabsModule, MatIconModule,
    TabPurchaseEntry, AdvanceBonusEntry,
    WorkerFinanceOwnerReview, WorkerFinanceAdminConfirm,
  ],
  template: `
    <div class="wf-page">
      <h2 class="page-title">Workers Finance</h2>
      <mat-tab-group animationDuration="150ms" (selectedTabChange)="onTabChange()">
        <mat-tab *ngIf="canEnter">
          <ng-template mat-tab-label><mat-icon>remove_shopping_cart</mat-icon>&nbsp;Purchases</ng-template>
          <div class="tab-body"><app-tab-purchase-entry /></div>
        </mat-tab>
        <mat-tab *ngIf="canEnter">
          <ng-template mat-tab-label><mat-icon>savings</mat-icon>&nbsp;Advance / Bonus</ng-template>
          <div class="tab-body"><app-advance-bonus-entry /></div>
        </mat-tab>
        <mat-tab *ngIf="canReview">
          <ng-template mat-tab-label><mat-icon>rate_review</mat-icon>&nbsp;Owner Review</ng-template>
          <div class="tab-body"><app-worker-finance-owner-review /></div>
        </mat-tab>
        <mat-tab *ngIf="canConfirm">
          <ng-template mat-tab-label><mat-icon>payments</mat-icon>&nbsp;Confirm Payment</ng-template>
          <div class="tab-body"><app-worker-finance-admin-confirm /></div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .wf-page { max-width: 1000px; }
    .page-title { font-size: 20px; font-weight: 600; margin: 0 0 16px; color: #1a1a2e; }
    .tab-body { padding: 16px 0; }
  `],
})
export class WorkerFinanceMain {
  @ViewChild(WorkerFinanceOwnerReview) private ownerReview?: WorkerFinanceOwnerReview;
  @ViewChild(WorkerFinanceAdminConfirm) private adminConfirm?: WorkerFinanceAdminConfirm;

  constructor(private auth: Auth) {}

  get role(): string { return this.auth.getRole() ?? ''; }
  get canEnter():   boolean { return ['ADMIN','MAIN_ACCOUNTANT','ACCOUNTANT'].includes(this.role); }
  get canReview():  boolean { return ['ADMIN','OWNER'].includes(this.role); }
  get canConfirm(): boolean { return this.role === 'ADMIN'; }

  onTabChange(): void {
    this.ownerReview?.load();
    this.adminConfirm?.load();
  }
}
