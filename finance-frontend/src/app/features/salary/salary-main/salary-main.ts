import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';
import { EnterSalary } from '../enter-salary/enter-salary';
import { SalaryReview } from '../salary-review/salary-review';
import { SalaryBalance } from '../salary-balance/salary-balance';
import { SalaryRecipientsPage } from '../recipients/salary-recipients';
import { Auth } from '../../../core/services/auth';

@Component({
  selector: 'app-salary-main',
  standalone: true,
  imports: [
    CommonModule, MatTabsModule, MatIconModule,
    EnterSalary, SalaryReview, SalaryBalance, SalaryRecipientsPage,
  ],
  template: `
    <div class="main-page">
      <h2 class="page-title">Salary</h2>
      <mat-tab-group animationDuration="150ms">
        <mat-tab *ngIf="canEnter">
          <ng-template mat-tab-label><mat-icon>payments</mat-icon>&nbsp;Enter Salary</ng-template>
          <div class="tab-body"><app-enter-salary /></div>
        </mat-tab>
        <mat-tab *ngIf="canReview">
          <ng-template mat-tab-label><mat-icon>fact_check</mat-icon>&nbsp;Salary Review</ng-template>
          <div class="tab-body"><app-salary-review /></div>
        </mat-tab>
        <mat-tab *ngIf="canReview">
          <ng-template mat-tab-label><mat-icon>account_balance_wallet</mat-icon>&nbsp;Balance</ng-template>
          <div class="tab-body"><app-salary-balance /></div>
        </mat-tab>
        <mat-tab *ngIf="canManageRecipients">
          <ng-template mat-tab-label><mat-icon>group</mat-icon>&nbsp;Recipients</ng-template>
          <div class="tab-body"><app-salary-recipients /></div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .main-page { max-width: 1200px; }
    .page-title { font-size: 20px; font-weight: 600; margin: 0 0 16px; color: #1a1a2e; }
    .tab-body { padding: 16px 0; }
  `],
})
export class SalaryMain {
  constructor(private auth: Auth) {}

  get role(): string { return this.auth.getRole() ?? ''; }
  get canEnter():            boolean { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT', 'ACCOUNTANT'].includes(this.role); }
  get canReview():           boolean { return ['ADMIN', 'OWNER', 'MAIN_ACCOUNTANT'].includes(this.role); }
  get canManageRecipients(): boolean { return this.role === 'ADMIN'; }
}
