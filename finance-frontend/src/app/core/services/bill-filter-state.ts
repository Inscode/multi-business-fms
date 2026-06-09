import { Injectable } from '@angular/core';

export interface BillFilterSnapshot {
  filterFrom: string;
  filterTo: string;
  selectedBusiness: string;
  selectedStatus: string;
  selectedArea: string;
  hideCompleted: boolean;
  overdueMode: boolean;
  searchQuery: string;
  pageIndex: number;
}

@Injectable({ providedIn: 'root' })
export class BillFilterState {
  snapshot: BillFilterSnapshot | null = null;

  save(s: BillFilterSnapshot): void { this.snapshot = { ...s }; }
  restore(): BillFilterSnapshot | null { return this.snapshot; }
  clear(): void { this.snapshot = null; }
}
