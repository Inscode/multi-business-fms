import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { WorkerPortalService, WorkerReturnOverview } from '../../../core/services/worker-portal';

@Component({
  selector: 'app-worker-returns-overview',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CommonModule, FormsModule, MatIconModule, MatProgressSpinnerModule, DecimalPipe],
  templateUrl: './worker-returns-overview.html',
  styleUrl: './worker-returns-overview.scss',
})
export class WorkerReturnsOverview implements OnInit {
  all: WorkerReturnOverview[] = [];
  filtered: WorkerReturnOverview[] = [];
  areas: string[] = [];

  loading = false;
  error = false;

  searchQuery = '';
  areaFilter = '';
  typeFilter = '';

  constructor(
    private svc: WorkerPortalService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.cdr.detectChanges();
    this.svc.getReturnsOverview().subscribe({
      next: items => {
        this.all = items;
        this.areas = [...new Set(items.map(r => r.area).filter((a): a is string => !!a))].sort();
        this.applyFilters();
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.error = true;
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  applyFilters(): void {
    const q = this.searchQuery.toLowerCase().trim();
    this.filtered = this.all.filter(r => {
      const matchSearch = !q || r.customerName.toLowerCase().includes(q) || r.billNumber.toLowerCase().includes(q);
      const matchArea = !this.areaFilter || r.area === this.areaFilter;
      const matchType = !this.typeFilter || r.returnType === this.typeFilter;
      return matchSearch && matchArea && matchType;
    });
    this.cdr.detectChanges();
  }

  clearSearch(): void {
    this.searchQuery = '';
    this.applyFilters();
  }

  typeColor(t: string): string {
    return t === 'DAMAGE' ? '#ef5350' : '#43a047';
  }

  typeIcon(t: string): string {
    return t === 'DAMAGE' ? 'broken_image' : 'recycling';
  }
}
