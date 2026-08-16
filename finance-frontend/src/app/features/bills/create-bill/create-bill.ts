import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { Router } from '@angular/router';
import { Bill, BillNumberOption } from '../../../core/services/bill';
import { Auth } from '../../../core/services/auth';
import { CustomerService } from '../../../core/services/customer';
import { localDateStr } from '../../../core/utils/date-utils';
import { DeliveryService, DeliveryRun, RouteArea, DeliveryMode }
  from '../../../core/services/delivery';

@Component({
  selector: 'app-create-bill',
  imports: [   CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatAutocompleteModule,
    CommonModule],
  templateUrl: './create-bill.html',
  styleUrl: './create-bill.scss',
})
export class CreateBill implements OnInit{
  form: FormGroup;
  loading = false;
  errorMsg = '';
  workers: WorkerResponse[] = [];
  allCustomers: { id: number; name: string; area?: string }[] = [];
  filteredCustomers: { id: number; name: string; area?: string }[] = [];
  selectedCustomerId: number | null = null;



  // Retail Shop and Hardware are not billed through this system; offering them only
  // invited a bill to be filed under a business nothing else reports on.
  businesses  = ['RAINCO', 'STATIONERY', 'PLASTIC'];
  divisions   = ['STORE', 'SHOP'];
  billTypes   = ['CASH', 'CREDIT'];
  billSources = ['SYSTEM', 'MANUAL', 'DRAFT'];

  get availableBillSources(): string[] {
    const business = this.form.get('business')?.value;
    if (business === 'RAINCO') {
      return ['SYSTEM', 'MANUAL', 'MANUAL_BOOK', 'DRAFT'];
    }
    if (business === 'PLASTIC' || business === 'STATIONERY') {
      return ['SYSTEM', 'MANUAL_BOOK', 'DRAFT'];
    }
    return ['SYSTEM', 'MANUAL', 'DRAFT'];
  }

  suggestedBillNumbers: BillNumberOption[] = [];

  missingCount(): number { return this.suggestedBillNumbers.filter(n => n.missing).length; }
  firstNewNumber(): number | null {
    return this.suggestedBillNumbers.find(n => !n.missing)?.number ?? null;
  }
  loadingNumbers = false;
  readonly String = String;

  get isDraft(): boolean {
    return this.form.get('billSource')?.value === 'DRAFT';
  }

  get isEditing(): boolean {
    return !!history.state?.editingBill;
  }

  get editingBill(): any {
    return history.state?.editingBill;
  }

  constructor(
    private fb: FormBuilder,
    private delivery: DeliveryService,
    private billService: Bill,
    private workerService: Worker,
    private customerService: CustomerService,
    private router: Router,
    private auth: Auth,
    private cdr: ChangeDetectorRef
  ) {
    this.form = this.fb.group({
      business:           ['RAINCO', Validators.required],
      division:           [null, Validators.required],
      billType:           [null, Validators.required],
      billSource:         ['SYSTEM', Validators.required],
      billNumber:         [''],
      area:               [null],
      customerName:       ['', Validators.required],
      totalAmount:        [null, [Validators.required, Validators.min(0.01)]],
      billDate:           [new Date()],
      workerId:           [null],
      notes:              [''],
      skippedBillNumbers: [''],
    });
  }

  get userDivision(): string {
    const role = this.auth.getRole();
    if (role == 'ACCOUNTANT') return 'STORE';
    if (role == 'SHOP_ACCOUNTANT') return 'SHOP';
    return '';
  }

  get isAdmin(): boolean {
    return this.auth.getRole() === 'ADMIN';
  }


  filterCustomers(value: string): void {
    const q = (value ?? '').toLowerCase();
    this.filteredCustomers = q.length === 0
      ? this.allCustomers.slice(0, 50)
      : this.allCustomers.filter(c => c.name.toLowerCase().includes(q)).slice(0, 50);
  }

  onCustomerSelected(name: string): void {
    const match = this.allCustomers.find(c => c.name === name);
    this.selectedCustomerId = match?.id ?? null;
    // Auto-fill area from customer profile if available
    if (match?.area) {
      this.form.get('area')?.setValue(match.area);
    }
  }

  onCustomerInput(): void {
    this.selectedCustomerId = null;
    this.form.get('area')?.setValue(null);
  }

  onCustomerBlur(): void {
    // Delay so optionSelected fires first — clicking a dropdown item blurs the
    // input before the selection event, making selectedCustomerId still null at blur time.
    setTimeout(() => {
      const val = (this.form.get('customerName')?.value ?? '').trim();
      if (val && !this.selectedCustomerId) {
        this.form.get('customerName')?.setValue('');
        this.form.get('area')?.setValue(null);
        this.form.get('customerName')?.markAsTouched();
        this.cdr.markForCheck();
      }
    }, 200);
  }

  ngOnInit(): void {
    this.loadWorkers();
    this.loadCustomers();
    // Which round is open decides what every bill entered here joins.
    if (!this.isEditing) this.loadRun();

    if (this.isEditing) {
      const b = this.editingBill;
      this.form.patchValue({
        business:     b.business,
        division:     b.division,
        billType:     b.billType,
        billSource:   b.billSource,
        customerName: b.customerName,
        totalAmount:  b.totalAmount,
        area:         b.area,
        billDate:     b.billDate ? new Date(b.billDate) : null,
        workerId:     b.workerId ?? null,
        notes:        b.notes ?? '',
        billNumber:   b.billNumber ?? '',
      });
      if (!this.isAdmin) {
        this.form.get('billSource')?.disable();
      }
      this.form.get('billNumber')?.clearValidators();
      this.form.get('billNumber')?.updateValueAndValidity();
    } else {
      this.watchBillSource();
      if (!this.isAdmin) {
        this.form.get('division')?.setValue(this.userDivision);
        this.form.get('division')?.disable();
      } else {
        // Admins enter store bills almost every time; shop is the exception, and
        // leaving it blank made them pick the same value on every bill.
        this.form.get('division')?.setValue('STORE');
      }
    }
  }

  private loadWorkers(): void {
    this.workerService.getAllWorkers().subscribe({
      next: (w) => { this.workers = w.filter(w => w.active); this.cdr.markForCheck(); },
      error: () => { this.workers = []; this.cdr.markForCheck(); }
    });
  }

  private loadCustomers(): void {
    this.customerService.getActive().subscribe({
      next: (list) => {
        this.allCustomers = list.map(c => ({ id: c.id, name: c.name, area: c.area }));
        this.filteredCustomers = this.allCustomers.slice(0, 50);

        // Restore selectedCustomerId when editing — the form patches customerName
        // text but has no customerId field, so we match by name after the list loads.
        if (this.isEditing && !this.selectedCustomerId) {
          const match = this.allCustomers.find(c => c.name === this.editingBill?.customerName);
          if (match) this.selectedCustomerId = match.id;
        }

        const ctrl = this.form.get('customerName');
        ctrl?.valueChanges.subscribe(v => this.filterCustomers(v ?? ''));
        this.cdr.markForCheck();
      },
      error: () => {}
    });
  }

  private watchBillSource(): void {
    const loadNumbers = () => {
      const business = this.form.get('business')?.value;
      const billSource = this.form.get('billSource')?.value;
      const billNumberControl = this.form.get('billNumber');

      // Continuation pages only apply to MANUAL_BOOK — drop anything typed before a switch
      if (billSource !== 'MANUAL_BOOK') {
        this.form.get('skippedBillNumbers')?.setValue('');
      }

      if (billSource === 'DRAFT') {
        billNumberControl?.clearValidators();
        billNumberControl?.setValue('');
        billNumberControl?.updateValueAndValidity();
        this.suggestedBillNumbers = [];
        return;
      }

      billNumberControl?.setValidators(Validators.required);
      billNumberControl?.updateValueAndValidity();

      if (business && billSource && billSource !== 'DRAFT') {
        this.loadingNumbers = true;
        this.billService.getNextBillNumbers(business, billSource).subscribe({
          next: (nums) => {
            this.suggestedBillNumbers = nums;
            // Default to the next fresh number, never to a gap — a missing number is a
            // question for someone to answer, not something to assign by accident.
            const firstNew = nums.find(n => !n.missing);
            if (firstNew && !this.form.get('billNumber')?.value && !this.isAdmin) {
              this.form.get('billNumber')?.setValue(String(firstNew.number));
            }
            this.loadingNumbers = false;
            this.cdr.markForCheck();
          },
          error: () => { this.loadingNumbers = false; this.cdr.markForCheck(); }
        });
      }
    };

    this.form.get('billSource')?.valueChanges.subscribe(loadNumbers);
    this.form.get('business')?.valueChanges.subscribe(() => {
      const currentSource = this.form.get('billSource')?.value;
      const available = this.availableBillSources;
      if (!available.includes(currentSource)) {
        this.form.get('billSource')?.setValue('SYSTEM');
      }
      loadNumbers();
    });

    // Load on init with default values
    loadNumbers();
  }

  // ── The lorry round ─────────────────────────────────────────────────
  // Fifteen to twenty bills in a row go to the same area, so the round is answered
  // once and then stays. The bar showing which one is deliberately loud: a sticky
  // default nobody notices is how bills end up on the wrong lorry.

  currentRun: DeliveryRun | null = null;
  routeAreas: RouteArea[] = [];
  loadingRun = false;
  openingRun = false;
  showRunPicker = false;
  newRunAreaIds: number[] = [];
  newRunDate: Date = new Date();
  runError = '';

  /** Overrides the run for this one bill — springs back to the round afterwards. */
  billMode: DeliveryMode | null = null;

  private loadRun(): void {
    this.loadingRun = true;
    this.delivery.current().subscribe({
      next: (run) => {
        this.currentRun = run;
        this.loadingRun = false;
        this.cdr.markForCheck();
      },
      error: () => { this.loadingRun = false; this.cdr.markForCheck(); },
    });
    this.delivery.areas().subscribe({
      next: (a) => { this.routeAreas = a; this.cdr.markForCheck(); },
      error: () => {},
    });
  }

  /**
   * True when the open run's date has passed. Yesterday's round must not quietly
   * collect today's bills, so it is called out rather than left as the default.
   */
  get runIsStale(): boolean {
    if (!this.currentRun) return false;
    return this.currentRun.plannedDate < localDateStr();
  }

  openRunPicker(): void {
    this.showRunPicker = true;
    this.newRunAreaIds = this.currentRun?.routeAreaIds ?? [];
    this.newRunDate = new Date();
    this.runError = '';
    this.cdr.markForCheck();
  }

  openRun(): void {
    if (!this.newRunAreaIds.length) { this.runError = 'Pick at least one route.'; return; }
    this.openingRun = true;
    this.runError = '';
    this.delivery.open(this.newRunAreaIds, localDateStr(this.newRunDate)).subscribe({
      next: (run) => {
        this.currentRun = run;
        this.showRunPicker = false;
        this.openingRun = false;
        this.cdr.markForCheck();
      },
      error: (e) => {
        this.openingRun = false;
        this.runError = e?.error?.message ?? 'Could not open the run.';
        this.cdr.markForCheck();
      },
    });
  }

  closeRun(): void {
    if (!this.currentRun) return;
    this.delivery.setStatus(this.currentRun.id, 'DISPATCHED').subscribe({
      next: () => { this.currentRun = null; this.cdr.markForCheck(); },
      error: () => {},
    });
  }

  /** What this bill will be saved as, given the run and any one-off override. */
  get effectiveMode(): DeliveryMode {
    if (this.billMode) return this.billMode;
    return this.currentRun ? 'ROUTE' : 'UNSPECIFIED';
  }

  setBillMode(mode: DeliveryMode | null): void {
    this.billMode = mode;
    this.cdr.markForCheck();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.selectedCustomerId) {
      this.form.get('customerName')?.setErrors({ mustSelect: true });
      this.form.get('customerName')?.markAsTouched();
      return;
    }

    this.loading = true;
    this.errorMsg = '';

    const raw = this.form.getRawValue();
    const skippedRaw: string = raw.skippedBillNumbers ?? '';
    const skippedBillNumbers = skippedRaw
      .split(',')
      .map((s: string) => s.trim())
      .filter((s: string) => s.length > 0);

    // A one-off override wins for this bill only; otherwise it joins the open round.
    const joiningRun = this.effectiveMode === 'ROUTE' && !!this.currentRun;

    const payload: any = {
      ...raw,
      deliveryMode: this.effectiveMode,
      deliveryRunId: joiningRun ? this.currentRun!.id : undefined,
      billDate: raw.billDate ? localDateStr(new Date(raw.billDate)) : localDateStr(),
      customerId: this.selectedCustomerId,
      skippedBillNumbers: skippedBillNumbers.length > 0 ? skippedBillNumbers : undefined,
    };
    delete payload.skippedBillNumbers; // remove string field
    if (skippedBillNumbers.length > 0) payload.skippedBillNumbers = skippedBillNumbers;
    if (!payload.workerId) delete payload.workerId;
    if (!payload.customerId) delete payload.customerId;

    if (this.isEditing) {
      this.billService.updateBill(this.editingBill.id, payload).subscribe({
        next: () => this.router.navigate(['/bills', this.editingBill.id]),
        error: () => {
          this.errorMsg = 'Failed to update bill. Please try again.';
          this.loading = false;
          this.cdr.markForCheck();
        }
      });
    } else {
      if (this.isDraft) delete payload.billNumber;
      this.billService.createBill(payload).subscribe({
        next: () => this.router.navigate(['/bills']),
        error: (err) => {
          const msg: string = err?.error?.message ?? err?.message ?? '';
          if (msg.toLowerCase().includes('already exists')) {
            this.form.get('billNumber')?.setErrors({ duplicate: true });
            this.form.get('billNumber')?.markAsTouched();
            this.errorMsg = msg;
          } else {
            this.errorMsg = 'Failed to create bill. Please try again.';
          }
          this.loading = false;
          this.cdr.markForCheck();
        }
      });
    }
  }

  cancel(): void {
    this.router.navigate(['/bills']);
  }

}
