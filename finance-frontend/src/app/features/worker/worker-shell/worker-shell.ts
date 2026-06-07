import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';
import { Auth } from '../../../core/services/auth';
import { WorkerTranslateService, Lang } from '../../../core/services/worker-translate';
import { WorkerBillList } from '../worker-bill-list/worker-bill-list';
import { WorkerPayments } from '../worker-payments/worker-payments';

@Component({
  selector: 'app-worker-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, MatIconModule, MatButtonModule, WorkerBillList, WorkerPayments],
  template: `
<div class="worker-app">

  <!-- Header -->
  <header class="w-header">
    <div class="w-header-left">
      <div class="w-avatar">{{ initial }}</div>
      <div class="w-name">{{ name }}</div>
    </div>
    <div class="lang-btns">
      <button [class.active]="tr.lang() === 'en'" (click)="setLang('en')">EN</button>
      <button [class.active]="tr.lang() === 'si'" (click)="setLang('si')">සි</button>
      <button [class.active]="tr.lang() === 'ta'" (click)="setLang('ta')">த</button>
      <button class="logout-btn" (click)="logout()">
        <mat-icon>logout</mat-icon>
      </button>
    </div>
  </header>

  <!-- Tab content -->
  <main class="w-content">
    <app-worker-bill-list *ngIf="activeTab === 'bills'"></app-worker-bill-list>
    <app-worker-payments *ngIf="activeTab === 'payments'"></app-worker-payments>
  </main>

  <!-- Bottom tab bar -->
  <nav class="w-tabs">
    <button class="w-tab" [class.w-tab-active]="activeTab === 'bills'" (click)="activeTab = 'bills'">
      <mat-icon>receipt_long</mat-icon>
      <span>{{ tr.t('bills') }}</span>
    </button>
    <button class="w-tab" [class.w-tab-active]="activeTab === 'payments'" (click)="activeTab = 'payments'">
      <mat-icon>payments</mat-icon>
      <span>{{ tr.t('payments') }}</span>
    </button>
  </nav>
</div>
  `,
  styleUrl: './worker-shell.scss',
})
export class WorkerShell implements OnInit {
  activeTab: 'bills' | 'payments' = 'bills';

  get initial(): string { return (this.auth.getCurrentUser()?.fullName ?? 'W').charAt(0).toUpperCase(); }
  get name(): string    { return this.auth.getCurrentUser()?.fullName ?? 'Worker'; }

  constructor(
    public tr: WorkerTranslateService,
    private auth: Auth,
    private router: Router,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    if (this.auth.getRole() !== 'WORKER') {
      this.router.navigate(['/dashboard']);
    }
  }

  setLang(lang: Lang): void {
    this.tr.set(lang);
    this.cdr.detectChanges();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
