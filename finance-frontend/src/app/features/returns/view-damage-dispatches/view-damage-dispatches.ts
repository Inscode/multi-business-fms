import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { DamageDispatchResponse, DamageDispatchService } from '../../../core/services/damage-dispatch';

@Component({
  selector: 'app-view-damage-dispatches',
  standalone: true,
  imports: [
    CommonModule, DatePipe, DecimalPipe,
    MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatExpansionModule,
  ],
  templateUrl: './view-damage-dispatches.html',
  styleUrl: './view-damage-dispatches.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ViewDamageDispatches implements OnInit {
  dispatches: DamageDispatchResponse[] = [];
  loading = false;
  expandedId: number | null = null;
  loadingDetail: number | null = null;
  detailCache = new Map<number, DamageDispatchResponse>();

  constructor(private service: DamageDispatchService, private cdr: ChangeDetectorRef) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.service.getAll().subscribe({
      next: (d) => { this.dispatches = d; this.loading = false; this.cdr.markForCheck(); },
      error: () => { this.loading = false; this.cdr.markForCheck(); },
    });
  }

  toggle(dispatch: DamageDispatchResponse): void {
    if (this.expandedId === dispatch.id) {
      this.expandedId = null;
      this.cdr.markForCheck();
      return;
    }
    this.expandedId = dispatch.id;
    if (this.detailCache.has(dispatch.id)) {
      this.cdr.markForCheck();
      return;
    }
    this.loadingDetail = dispatch.id;
    this.cdr.markForCheck();
    this.service.getById(dispatch.id).subscribe({
      next: (d) => {
        this.detailCache.set(d.id, d);
        this.loadingDetail = null;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingDetail = null; this.cdr.markForCheck(); },
    });
  }

  detailFor(id: number): DamageDispatchResponse | undefined {
    return this.detailCache.get(id);
  }
}
