import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, HostListener, OnInit, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Auth } from '../../../core/services/auth';
import { DeliveryRun, DeliveryService } from '../../../core/services/delivery';
import { ExpenseAdvanceResponse, ExpenseBusFare, ExpenseService } from '../../../core/services/expense';
import { Worker, WorkerResponse } from '../../../core/services/worker';
import { compressImage, describeSaving } from '../../../core/utils/image-compress';

interface SettlementLine { category: string; amount: number | null; description: string; busFareId: number | null; receiptImageUrl: string; uploading: boolean; photoError: string; photoSizeNote: string; }

@Component({
  selector: 'app-expense-advance-panel', standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatIconModule],
  templateUrl: './expense-advance-panel.html', styleUrl: './expense-advance-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseAdvancePanel implements OnInit {
  @Output() changed = new EventEmitter<void>();
  advances: ExpenseAdvanceResponse[] = [];
  runs: DeliveryRun[] = [];
  collectors: WorkerResponse[] = [];
  fares: ExpenseBusFare[] = [];
  loading = false;
  showIssue = false;
  showFareManager = false;
  issueError = '';
  issuing = false;
  business = 'RAINCO';
  targetType: 'DELIVERY_ROUTE' | 'COLLECTION_DAY' = 'DELIVERY_ROUTE';
  runId: number | null = null;
  collectorId: number | null = null;
  activityDate = this.today();
  advanceAmount: number | null = null;
  advanceNotes = '';
  settling: ExpenseAdvanceResponse | null = null;
  settlementLines: SettlementLine[] = [];
  settlingNow = false;
  settleError = '';
  activePhotoLine: SettlementLine | null = null;
  fareFrom = '';
  fareTo = '';
  fareAmount: number | null = null;
  fareError = '';
  editingFareId: number | null = null;
  addingFare = false;
  readonly businesses = ['RAINCO', 'PLASTIC'];
  readonly categories = [
    { value: 'FUEL', label: 'Fuel' }, { value: 'TEA', label: 'Tea' },
    { value: 'PARKING', label: 'Parking' }, { value: 'REPAIR', label: 'Repair' },
    { value: 'DRIVER_SALARY', label: 'Driver Salary' },
  ];

  constructor(private expenses: ExpenseService, private delivery: DeliveryService,
              private worker: Worker, private auth: Auth, private cdr: ChangeDetectorRef) {}

  get isAdmin(): boolean { return this.auth.getRole() === 'ADMIN'; }
  get canManageAdvances(): boolean { return ['ADMIN', 'MAIN_ACCOUNTANT', 'ACCOUNTANT'].includes(this.auth.getRole() ?? ''); }
  get activeFares(): ExpenseBusFare[] { return this.fares.filter(f => f.active); }
  categoriesFor(targetType: ExpenseAdvanceResponse['targetType']): { value: string; label: string }[] {
    return targetType === 'COLLECTION_DAY'
      ? this.categories.filter(category => !['PARKING', 'DRIVER_SALARY'].includes(category.value))
      : this.categories;
  }
  get spentTotal(): number { return this.roundMoney(this.settlementLines.reduce((sum, line) => sum + (Number(line.amount) || 0), 0)); }
  get returnAmount(): number { return this.settling ? this.roundMoney(Math.max(0, Number(this.settling.amount) - this.spentTotal)) : 0; }
  get isOverAdvance(): boolean { return !!this.settling && this.spentTotal > Number(this.settling.amount); }
  get canSettle(): boolean { return !!this.settling && !this.settlingNow && !this.isOverAdvance
    && this.settlementLines.every(l => !!l.category && !!l.amount && l.amount > 0 && !l.uploading && (!this.lineNeedsPhoto(l) || !!l.receiptImageUrl)); }

  ngOnInit(): void {
    this.load();
    const now = new Date(); const from = new Date(now); from.setMonth(from.getMonth() - 6);
    const to = new Date(now); to.setMonth(to.getMonth() + 1);
    this.delivery.list(this.iso(from), this.iso(to)).subscribe({ next: runs => { this.runs = runs.filter(run => run.status !== 'CANCELLED'); this.cdr.markForCheck(); }, error: () => {} });
    this.worker.getWorkersByType('DELIVERY').subscribe({ next: workers => { this.collectors = workers.filter(w => w.active); this.cdr.markForCheck(); }, error: () => {} });
    this.loadFares();
  }

  load(): void { this.loading = true; this.expenses.getAdvances().subscribe({
    next: rows => { this.advances = rows; this.loading = false; this.cdr.markForCheck(); },
    error: () => { this.loading = false; this.cdr.markForCheck(); },
  }); }
  loadFares(): void { this.expenses.getBusFares().subscribe({ next: rows => { this.fares = rows; this.cdr.markForCheck(); }, error: () => {} }); }

  issue(): void {
    this.issueError = '';
    if (!this.advanceAmount || this.advanceAmount <= 0 || (this.targetType === 'DELIVERY_ROUTE' && !this.runId)
      || (this.targetType === 'COLLECTION_DAY' && (!this.collectorId || !this.activityDate))) return;
    const selectedRun = this.runs.find(r => r.id === this.runId);
    this.issuing = true;
    this.expenses.issueAdvance({ business: this.business, targetType: this.targetType,
      deliveryRunId: this.targetType === 'DELIVERY_ROUTE' ? this.runId! : undefined,
      collectorId: this.targetType === 'COLLECTION_DAY' ? this.collectorId! : undefined,
      activityDate: this.targetType === 'DELIVERY_ROUTE' ? selectedRun!.plannedDate : this.activityDate,
      amount: this.advanceAmount, notes: this.advanceNotes || undefined,
    }).subscribe({
      next: () => { this.issuing = false; this.showIssue = false; this.advanceAmount = null; this.advanceNotes = ''; this.load(); this.changed.emit(); this.cdr.markForCheck(); },
      error: e => { this.issuing = false; this.issueError = e?.error?.message ?? 'Could not issue cash advance.'; this.cdr.markForCheck(); },
    });
  }

  beginSettle(advance: ExpenseAdvanceResponse): void {
    this.settling = advance; this.settleError = ''; this.activePhotoLine = null; this.settlementLines = [this.newLine()];
  }
  cancelSettle(): void { this.settling = null; this.activePhotoLine = null; this.settlementLines = []; this.settleError = ''; }
  newLine(): SettlementLine { return { category: '', amount: null, description: '', busFareId: null, receiptImageUrl: '', uploading: false, photoError: '', photoSizeNote: '' }; }
  addLine(): void { this.settlementLines = [...this.settlementLines, this.newLine()]; }
  removeLine(i: number): void {
    const removed = this.settlementLines[i];
    if (this.activePhotoLine === removed) this.activePhotoLine = null;
    this.settlementLines = this.settlementLines.filter((_, n) => n !== i);
  }
  lineNeedsPhoto(line: SettlementLine): boolean { return !!line.category && !['TEA', 'BUS_FARE', 'DRIVER_SALARY'].includes(line.category); }
  categoryChanged(line: SettlementLine): void { line.busFareId = null; line.amount = null; line.receiptImageUrl = ''; line.photoError = ''; }
  fareChanged(line: SettlementLine): void {
    const fare = this.activeFares.find(f => f.id === Number(line.busFareId));
    line.amount = fare?.amount ?? null;
    line.description = fare ? `${fare.fromLocation} → ${fare.toLocation}` : '';
  }
  uploadLinePhoto(line: SettlementLine, event: Event): void {
    const input = event.target as HTMLInputElement; const file = input.files?.[0]; if (!file) return;
    input.value = '';
    this.acceptLinePhoto(line, file);
  }
  setActivePhotoLine(line: SettlementLine): void { this.activePhotoLine = line; }
  @HostListener('document:paste', ['$event'])
  onPaste(event: ClipboardEvent): void {
    if (!this.settling || !this.activePhotoLine || !this.lineNeedsPhoto(this.activePhotoLine)) return;
    const image = Array.from(event.clipboardData?.items ?? []).find(item => item.kind === 'file' && item.type.startsWith('image/'));
    const file = image?.getAsFile();
    if (!file) return;
    event.preventDefault();
    this.acceptLinePhoto(this.activePhotoLine, file);
  }
  private acceptLinePhoto(line: SettlementLine, file: File): void {
    line.photoError = '';
    if (!file.type.startsWith('image/')) { line.photoError = 'Choose an image file.'; return; }
    line.uploading = true; this.cdr.markForCheck();
    compressImage(file, 150 * 1024).then(compressed => {
      line.photoSizeNote = `Photo size: ${describeSaving(compressed)}`;
      if (compressed.bytes > 10 * 1024 * 1024) {
        line.photoError = 'Compressed photo is still too large. Choose a smaller image.'; line.uploading = false; this.cdr.markForCheck(); return;
      }
      this.expenses.uploadImage(compressed.file).subscribe({
        next: res => { line.receiptImageUrl = res.url; line.uploading = false; this.cdr.markForCheck(); },
        error: () => { line.photoError = 'Could not upload photo.'; line.uploading = false; this.cdr.markForCheck(); },
      });
    });
  }
  settle(): void {
    if (!this.settling || this.isOverAdvance || this.settlementLines.some(l => !l.category || !l.amount || l.amount <= 0 || l.uploading || (this.lineNeedsPhoto(l) && !l.receiptImageUrl))) return;
    this.settlingNow = true; this.settleError = '';
    const items = this.settlementLines.map(line => ({ category: line.category, amount: Number(line.amount), description: line.description || undefined,
      receiptImageUrl: line.receiptImageUrl || undefined, busFareId: line.busFareId || undefined }));
    this.expenses.settleAdvance(this.settling.id, { returnedAmount: this.returnAmount, items }).subscribe({
      next: () => { this.settlingNow = false; this.cancelSettle(); this.load(); this.changed.emit(); this.cdr.markForCheck(); },
      error: e => { this.settlingNow = false; this.settleError = e?.error?.message ?? 'Could not close this advance.'; this.cdr.markForCheck(); },
    });
  }

  addFare(): void {
    this.fareError = '';
    if (!this.fareFrom.trim() || !this.fareTo.trim() || !this.fareAmount || this.fareAmount <= 0) return;
    this.addingFare = true;
    const payload = { fromLocation: this.fareFrom, toLocation: this.fareTo, amount: this.fareAmount };
    const save = this.editingFareId ? this.expenses.updateBusFare(this.editingFareId, payload) : this.expenses.addBusFare(payload);
    save.subscribe({
      next: () => { this.fareFrom = ''; this.fareTo = ''; this.fareAmount = null; this.editingFareId = null; this.addingFare = false; this.loadFares(); this.cdr.markForCheck(); },
      error: e => { this.fareError = e?.error?.message ?? 'Could not add fare route.'; this.addingFare = false; this.cdr.markForCheck(); },
    });
  }
  editFare(fare: ExpenseBusFare): void { this.editingFareId = fare.id; this.fareFrom = fare.fromLocation; this.fareTo = fare.toLocation; this.fareAmount = fare.amount; }
  cancelFareEdit(): void { this.editingFareId = null; this.fareFrom = ''; this.fareTo = ''; this.fareAmount = null; this.fareError = ''; }
  toggleFare(fare: ExpenseBusFare): void { this.expenses.setBusFareActive(fare.id, !fare.active).subscribe({ next: () => this.loadFares(), error: () => {} }); }
  runLabel(run: DeliveryRun): string { return `${run.areaName || 'Route'} · ${run.plannedDate} · #${run.id}`; }
  private today(): string { return this.iso(new Date()); }
  private roundMoney(value: number): number { return Math.round((value + Number.EPSILON) * 100) / 100; }
  private iso(date: Date): string { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`; }
}
