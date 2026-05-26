import { CommonModule, DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Payment, PaymentResponse } from '../../../core/services/payment';
import { Auth } from '../../../core/services/auth';
import { Router, RouterLink } from '@angular/router';
import { ReturnChequeDialog } from '../return-cheque-dialog/return-cheque-dialog';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-payment-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatMenuModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    DecimalPipe,
    LowerCasePipe,
    DatePipe,
    RouterLink,
    MatInputModule
  ],
  templateUrl: './payment-list.html',
  styleUrl: './payment-list.scss',
})

export class PaymentList implements OnInit {
  payments: PaymentResponse[] = [];
  loading = true;
  error = false;

  searchQuery = '';
  selectedArea = '';
  selectedStatus = '';

  allPayments: PaymentResponse[] = [];

  areas = [
    'Badalkumbura', 'Badulla', 'Bandarawela', 'Beragala',
    'Bogakumbura', 'Boralanda', 'Diyatalawa', 'Ella',
    'Etampitiya', 'Haldummulla', 'Hali-Ela', 'Hasalaka', 'Haputale',
    'Kandaketiya', 'Kumbalwela', 'Lunugala', 'Mahiyanganaya',
    'Meegahakivula', 'Passara', 'Uva-Paranagama', 'Welimada'
  ];

  
  statuses = ['', 'ENTERED', 'CONFIRMED', 'REJECTED', 'RETURNED'];

  displayedColumns = ['billNumber', 'customerName', 'amount',
                      'type', 'enteredBy', 'date', 'status', 'actions'];

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get isAccountant(): boolean { return this.auth.getRole() === 'ACCOUNTANT'; }
  get isOwner(): boolean { return this.auth.getRole() === 'OWNER';}

  constructor(
    private paymentService: Payment,
    private auth: Auth,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private dialog: MatDialog
  ) {}

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.cdr.detectChanges();
    this.error = false;

    this.paymentService.getAllPayments(
      this.selectedStatus || undefined
    ).subscribe({
      next: (p) => {
        this.allPayments = p;
        this.loading  = false;
        this.applyFilters();
        this.cdr.detectChanges();
      },
      error: () => {
        this.error   = true;
        this.loading = false;
        this.cdr.detectChanges();
      }
    });
  }

  applyFilters(): void {
    const query = this.searchQuery.toLowerCase().trim();

    this.payments = this.allPayments.filter(p => {
      const matchesSearch = !query || 
      p.customerName.toLowerCase().includes(query) || 
      (p.billNumber ?? '').toLowerCase().includes(query);

      const matchesArea = !this.selectedArea ||
      p.area === this.selectedArea;

      return matchesArea && matchesSearch;
    })

    this.cdr.detectChanges();
  }

  onSearchChange(): void {this.applyFilters();}
  onAreaChange(): void {this.applyFilters()};
  onFilterChange(): void { this.load(); }

  editPayment(payment: PaymentResponse): void {
    this.router.navigate(['/payments/enter'], { state: { payment } });
  }

  confirmPayment(id: number): void {
    this.paymentService.confirmPayment(id).subscribe({
      next: () => this.load(),
      error: () => alert('Failed to confirm payment.')
    });
  }

  canEdit(payment: PaymentResponse): boolean {
    return !this.isOwner && payment.status === 'ENTERED' && (this.isAccountant || this.isAdmin);
  }

  canReturn(payment: PaymentResponse): boolean {
    return payment.status === 'CONFIRMED' &&
           payment.paymentType === 'CHEQUE' &&
           this.isAdmin;
  }

  canDelete(payment: PaymentResponse): boolean {
    return this.isAdmin && payment.status === 'ENTERED';
  }

  hasActions(payment: PaymentResponse): boolean {
    return this.canEdit(payment) || this.canReturn(payment) || this.canDelete(payment);
  }

  deletePayment(payment: PaymentResponse): void {
    if (!confirm(`Delete payment of Rs ${payment.paymentAmount} for ${payment.billNumber}? This cannot be undone.`)) return;
    this.paymentService.deletePayment(payment.id).subscribe({
      next: () => this.load(),
      error: (err) => alert(err?.error?.message ?? 'Failed to delete payment.'),
    });
  }     
  
  
  returnCheque(payment: PaymentResponse): void {
    const ref = this.dialog.open(ReturnChequeDialog, {
      data: {
        paymentId: payment.id,
        billNumber: payment.billNumber
      }, 
      width: '460px',
      disableClose: true
    });

    ref.afterClosed().subscribe(confirmed => {
      if (confirmed) this.load();
    })
  }





}
