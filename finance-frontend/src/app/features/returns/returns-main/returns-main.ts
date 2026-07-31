import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { SubmitReturn } from '../submit-return/submit-return';
import { ReviewReturns } from '../review-returns/review-returns';
import { ReturnProductsPage } from '../../admin/return-products/return-products';
import { SubmitDamageDispatch } from '../submit-damage-dispatch/submit-damage-dispatch';
import { ViewDamageDispatches } from '../view-damage-dispatches/view-damage-dispatches';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-returns-main',
  standalone: true,
  imports: [
    CommonModule, MatTabsModule, MatIconModule,
    SubmitReturn, ReviewReturns, ReturnProductsPage,
    SubmitDamageDispatch, ViewDamageDispatches,
  ],
  template: `
    <div class="main-page">
      <h2 class="page-title">Returns</h2>
      <mat-tab-group animationDuration="150ms">
        <mat-tab *ngIf="canSubmit">
          <ng-template mat-tab-label><mat-icon>assignment_return</mat-icon>&nbsp;Submit Return</ng-template>
          <div class="tab-body"><app-submit-return /></div>
        </mat-tab>
        <mat-tab *ngIf="canReview">
          <ng-template mat-tab-label><mat-icon>rate_review</mat-icon>&nbsp;Review Returns</ng-template>
          <div class="tab-body"><app-review-returns [readonly]="isReadOnlyReturns" /></div>
        </mat-tab>
        <mat-tab *ngIf="isAdmin">
          <ng-template mat-tab-label><mat-icon>local_shipping</mat-icon>&nbsp;Send to Company</ng-template>
          <div class="tab-body"><app-submit-damage-dispatch /></div>
        </mat-tab>
        <mat-tab *ngIf="isAdmin">
          <ng-template mat-tab-label><mat-icon>history</mat-icon>&nbsp;Dispatch History</ng-template>
          <div class="tab-body"><app-view-damage-dispatches /></div>
        </mat-tab>
        <mat-tab *ngIf="canManageProducts">
          <ng-template mat-tab-label><mat-icon>inventory_2</mat-icon>&nbsp;Return Products</ng-template>
          <div class="tab-body"><app-return-products [readonly]="!isAdminProducts" /></div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .main-page { max-width: 1100px; }
    .page-title { font-size: 20px; font-weight: 600; margin: 0 0 16px; color: #1a1a2e; }
    .tab-body { padding: 16px 0; }
  `],
})
export class ReturnsMain {
  constructor(private auth: Auth) {}

  get role(): string { return this.auth.getRole() ?? ''; }
  get isAdmin():           boolean { return this.role === 'ADMIN'; }
  get canSubmit():         boolean { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.role); }
  get canReview():         boolean { return ['ADMIN', 'OWNER', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.role); }
  get isReadOnlyReturns(): boolean { return !['ADMIN', 'OWNER'].includes(this.role); }
  get canManageProducts(): boolean { return ['ADMIN', 'ACCOUNTANT', 'MAIN_ACCOUNTANT'].includes(this.role); }
  get isAdminProducts():   boolean { return this.role === 'ADMIN'; }
}
