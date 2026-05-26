import { CommonModule, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EditRequestResponse, EditRequestService } from '../../../core/services/edit-request';
import { ConfirmDialog } from '../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-edit-requests',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    DatePipe,
  ],
  templateUrl: './edit-requests.html',
  styleUrl: './edit-requests.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditRequestsPage implements OnInit {
  requests: EditRequestResponse[] = [];
  loading = true;
  error = false;

  showAll = false;

  get displayed(): EditRequestResponse[] {
    return this.showAll ? this.requests : this.requests.filter(r => r.status === 'PENDING');
  }

  get pendingCount(): number {
    return this.requests.filter(r => r.status === 'PENDING').length;
  }

  constructor(
    private editRequestService: EditRequestService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.error = false;
    this.editRequestService.getAll().subscribe({
      next: (r) => {
        this.requests = r;
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

  parsedChanges(changesJson: string): { key: string; value: string }[] {
    try {
      const obj = JSON.parse(changesJson);
      return Object.entries(obj)
        .filter(([, v]) => v !== null && v !== '' && v !== undefined)
        .map(([key, value]) => ({ key: this.labelFor(key), value: String(value) }));
    } catch {
      return [];
    }
  }

  private labelFor(key: string): string {
    const map: Record<string, string> = {
      customerName: 'Customer Name',
      totalAmount:  'Total Amount',
      billType:     'Bill Type',
      division:     'Division',
      area:         'Area',
      billDate:     'Bill Date',
      notes:        'Notes',
      amount:       'Amount',
      paymentType:  'Payment Type',
      paymentDate:  'Payment Date',
      chequeNumber: 'Cheque No.',
      chequeDate:   'Cheque Date',
      bankName:     'Bank',
      branchName:   'Branch',
      referenceNumber: 'Reference No.',
    };
    return map[key] ?? key;
  }

  approve(req: EditRequestResponse): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Approve Edit Request',
        message: `Apply the requested changes to ${req.targetRef}?`,
        confirmText: 'Approve',
        confirmColor: 'primary',
      },
      width: '360px',
    }).afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.editRequestService.approve(req.id).subscribe({
        next: () => this.load(),
        error: () => alert('Failed to approve request.'),
      });
    });
  }

  reject(req: EditRequestResponse): void {
    const reason = prompt('Rejection reason (optional):') ?? '';
    this.editRequestService.reject(req.id, reason).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to reject request.'),
    });
  }
}