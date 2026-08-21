import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ConfirmDialog } from '../../shared/confirm-dialog/confirm-dialog';
import { Auth } from '../../core/services/auth';
import { DeliveryRun, DeliveryService, MonthBusinessSummary, RouteArea, RunStatus }
  from '../../core/services/delivery';
import { localDateStr } from '../../core/utils/date-utils';
import { BillReturnService, ReturnImage } from '../../core/services/bill-return';
import { compressImage } from '../../core/utils/image-compress';

/**
 * Lorry rounds: what went out, where, and to whom.
 *
 * <p>This is the screen the admin checks a round against. The lorry says it made
 * fourteen drops; the run says fourteen customers and eighteen bills worth this much.
 * Reconstructing that by filtering bills on area and date would quietly include a bill
 * that shared both but travelled some other way, which is the whole reason a run is a
 * record rather than a query.
 */
@Component({
  selector: 'app-deliveries-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink,
            MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule,
            MatSelectModule, MatDatepickerModule, MatNativeDateModule,
            MatProgressSpinnerModule, MatTooltipModule, MatDialogModule, MatSnackBarModule],
  templateUrl: './deliveries-page.html',
  styleUrl: './deliveries-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DeliveriesPage implements OnInit {
  private api = inject(DeliveryService);
  private auth = inject(Auth);
  private dialog = inject(MatDialog);
  private snack = inject(MatSnackBar);
  private cdr = inject(ChangeDetectorRef);
  private returns = inject(BillReturnService);

  get isAdmin(): boolean {
    const r = this.auth.getRole();
    return r === 'ADMIN' || r === 'OWNER';
  }

  runs: DeliveryRun[] = [];
  areas: RouteArea[] = [];
  selected: DeliveryRun | null = null;
  loading = true;
  loadingDetail = false;

  from = this.monthsAgo(2);
  to = this.monthsAhead(1);

  // Opening a run from here as well as from Create Bill — the round is often planned
  // before anyone sits down to enter its bills.
  showOpen = false;
  newAreaIds: number[] = [];
  newDate = new Date();
  /** The month the round counts against — often not the month it leaves. */
  newMonth = DeliveriesPage.monthValue(new Date());
  newNotes = '';
  opening = false;
  openError = '';

  // Routes, admin only.
  showRoutes = false;
  newRouteName = '';

  // ── Month ────────────────────────────────────────────────────────
  // A round planned for the end of one month often goes out at the start of the next,
  // so which month it counts against is chosen rather than derived from the date.

  filterMonth = '';

  /** This month and the fifteen before, plus the next — rounds get planned ahead. */
  static readonly MONTHS: { value: string; label: string }[] = (() => {
    const out: { value: string; label: string }[] = [];
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() + 1);
    for (let i = 0; i < 17; i++) {
      out.push({
        value: DeliveriesPage.monthValue(d),
        label: d.toLocaleDateString('en-GB', { month: 'short', year: 'numeric' }),
      });
      d.setMonth(d.getMonth() - 1);
    }
    return out;
  })();

  readonly months = DeliveriesPage.MONTHS;

  static monthValue(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }

  // ── The month, by business (admin) ───────────────────────────────
  // A single lorry's counts say nothing about how the month is going, and the
  // businesses are stocked and settled separately, so they are shown apart.

  summary: MonthBusinessSummary[] = [];
  summaryMode = '';
  loadingSummary = false;

  loadSummary(): void {
    if (!this.isAdmin) return;
    this.loadingSummary = true;
    this.cdr.markForCheck();
    this.api.monthSummary(this.filterMonth || undefined, this.summaryMode || undefined)
      .subscribe({
        next: (rows) => { this.summary = rows; this.loadingSummary = false; this.cdr.markForCheck(); },
        error: () => { this.loadingSummary = false; this.cdr.markForCheck(); },
      });
  }

  get summaryTotals(): { sales: number; paid: number; pending: number; bills: number } {
    return this.summary.reduce((t, r) => ({
      sales:   t.sales   + (Number(r.sales)   || 0),
      paid:    t.paid    + (Number(r.paid)    || 0),
      pending: t.pending + (Number(r.pending) || 0),
      bills:   t.bills   + (Number(r.billCount) || 0),
    }), { sales: 0, paid: 0, pending: 0, bills: 0 });
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading = true;
    this.cdr.markForCheck();
    this.loadSummary();
    this.api.list(
      this.filterMonth ? undefined : localDateStr(this.from),
      this.filterMonth ? undefined : localDateStr(this.to),
      this.filterMonth || undefined,
    ).subscribe({
      next: (r) => { this.runs = r; this.loading = false; this.cdr.markForCheck(); },
      error: () => { this.loading = false; this.cdr.markForCheck(); },
    });
    this.api.areas(true).subscribe({
      next: (a) => { this.areas = a; this.cdr.markForCheck(); },
      error: () => {},
    });
  }

  select(run: DeliveryRun): void {
    this.loadingDetail = true;
    this.cdr.markForCheck();
    this.api.detail(run.id).subscribe({
      next: (d) => {
        this.selected = d;
        this.loadingDetail = false;
        this.loadRunImages(d.id);
        this.cdr.markForCheck();
      },
      error: () => { this.loadingDetail = false; this.cdr.markForCheck(); },
    });
  }

  // ── Opening ──────────────────────────────────────────────────────

  openRun(): void {
    if (!this.newAreaIds.length) { this.openError = 'Pick at least one route.'; return; }
    this.opening = true;
    this.openError = '';
    this.api.open(this.newAreaIds, localDateStr(this.newDate),
                  this.newMonth || undefined, this.newNotes || undefined)
      .subscribe({
        next: () => {
          this.showOpen = false;
          this.newAreaIds = [];
          this.newNotes = '';
          this.opening = false;
          this.load();
        },
        error: (e) => {
          this.opening = false;
          this.openError = e?.error?.message ?? 'Could not open the run.';
          this.cdr.markForCheck();
        },
      });
  }

  // ── Status ───────────────────────────────────────────────────────

  setStatus(run: DeliveryRun, status: RunStatus): void {
    const label = status === 'DISPATCHED' ? 'Mark dispatched'
                : status === 'COMPLETED'  ? 'Mark completed'
                : 'Cancel run';
    this.dialog.open(ConfirmDialog, {
      data: {
        title: label,
        message: status === 'CANCELLED'
          // Said plainly: the bills survive, so nobody thinks this deletes work.
          ? `Cancel ${run.areaName} on ${run.plannedDate}?\n\nThe ${run.billCount} bill(s) `
            + 'stay exactly as they are — they simply stop belonging to this run.'
          : `${label} for ${run.areaName} on ${run.plannedDate}?`,
        confirmText: label,
        confirmColor: status === 'CANCELLED' ? 'warn' : 'primary',
      },
      maxWidth: '95vw',
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.api.setStatus(run.id, status).subscribe({
        next: () => {
          this.snack.open('Run updated.', 'OK', { duration: 2500 });
          if (this.selected?.id === run.id) this.select(run);
          this.load();
        },
        error: (e) => this.snack.open(e?.error?.message ?? 'Failed.', 'OK', { duration: 5000 }),
      });
    });
  }

  removeBill(billId: number): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Take off this run',
        message: 'The bill stays as it is — it just stops belonging to this round.',
        confirmText: 'Take off',
        confirmColor: 'warn',
      },
    }).afterClosed().subscribe(result => {
      if (!result?.confirmed) return;
      this.api.removeBill(billId).subscribe({
        next: () => { if (this.selected) this.select(this.selected); this.load(); },
        error: () => this.snack.open('Failed.', 'OK', { duration: 4000 }),
      });
    });
  }

  // ── Routes (admin) ───────────────────────────────────────────────

  addRoute(): void {
    const name = this.newRouteName.trim();
    if (!name) return;
    this.api.saveArea({ name, active: true, sortOrder: 0 }).subscribe({
      next: () => {
        this.newRouteName = '';
        this.snack.open(`${name} added.`, 'OK', { duration: 2500 });
        this.load();
      },
      error: (e) => this.snack.open(e?.error?.message ?? 'Failed.', 'OK', { duration: 5000 }),
    });
  }

  toggleRoute(a: RouteArea): void {
    // Deactivated rather than deleted: a route that has carried bills is part of their
    // history, and removing it would orphan every run that used it.
    this.api.saveArea({ ...a, active: !a.active }).subscribe({
      next: () => this.load(),
      error: () => this.snack.open('Failed.', 'OK', { duration: 4000 }),
    });
  }

  // ── Adding bills after the fact ──────────────────────────────────
  // The common miss: fifteen bills were entered before anyone opened the run, or one
  // was entered after it closed. Re-keying them would be absurd, so they are picked
  // from the bills that belong to no round.

  showAdd = false;
  candidates: any[] = [];
  loadingCandidates = false;
  picked = new Set<number>();
  candidateSearch = '';
  assigning = false;

  openAdd(): void {
    if (!this.selected) return;
    this.showAdd = true;
    this.picked.clear();
    this.candidateSearch = '';
    this.loadingCandidates = true;
    this.cdr.markForCheck();
    // detectChanges, not markForCheck: the list arrives from outside anything the app
    // is already re-rendering, and marking alone left it sitting invisible until some
    // unrelated tap happened to trigger a pass.
    this.api.candidates(this.selected.id).subscribe({
      next: (c) => { this.candidates = c; this.loadingCandidates = false; this.cdr.detectChanges(); },
      error: () => { this.loadingCandidates = false; this.cdr.detectChanges(); },
    });
  }

  get shownCandidates(): any[] {
    const q = this.candidateSearch.trim().toLowerCase();
    if (!q) return this.candidates;
    return this.candidates.filter(b =>
      (b.billNumber ?? '').toLowerCase().includes(q) ||
      (b.customerName ?? '').toLowerCase().includes(q) ||
      (b.area ?? '').toLowerCase().includes(q));
  }

  togglePick(id: number): void {
    if (this.picked.has(id)) this.picked.delete(id);
    else this.picked.add(id);
    this.cdr.markForCheck();
  }

  /** Everything showing, so a filtered list can be taken in one go. */
  pickAllShown(): void {
    this.shownCandidates.forEach(b => this.picked.add(b.id));
    this.cdr.markForCheck();
  }

  assignPicked(): void {
    if (!this.selected || this.picked.size === 0) return;
    this.assigning = true;
    this.cdr.markForCheck();
    const runId = this.selected.id;
    this.api.assignBills(runId, [...this.picked]).subscribe({
      next: (r) => {
        this.assigning = false;
        this.showAdd = false;
        this.snack.open(`${r.assigned} bill(s) added to the run.`, 'OK', { duration: 3000 });
        this.select({ id: runId } as any);
        this.load();
      },
      error: (e) => {
        this.assigning = false;
        this.snack.open(e?.error?.message ?? 'Failed.', 'OK', { duration: 5000 });
        this.cdr.markForCheck();
      },
    });
  }

  // ── Return book pages ────────────────────────────────────────────
  // The morning after the lorry is back, the returns from every shop on the round are
  // written down a page of a book. That page is the evidence for the whole round, and
  // a long round runs to two or three — so pages are added freely, and more can be
  // added later when the count is finished.

  /** Iterated in the template, so it carries the union type rather than plain strings. */
  readonly bookTypes: ('SALABLE' | 'DAMAGE')[] = ['SALABLE', 'DAMAGE'];

  runImages: Record<'DAMAGE' | 'SALABLE', ReturnImage[]> = { DAMAGE: [], SALABLE: [] };
  uploadingPage: 'DAMAGE' | 'SALABLE' | null = null;
  pageError = '';

  private loadRunImages(runId: number): void {
    // A filter left over from the run before would silently hide bills on this one.
    this.bizFilter = null;
    this.returns.runImages(runId).subscribe({
      next: (imgs) => {
        this.runImages = {
          DAMAGE:  imgs.filter(i => i.returnType === 'DAMAGE'),
          SALABLE: imgs.filter(i => i.returnType === 'SALABLE'),
        };
        this.cdr.markForCheck();
      },
      error: () => {},
    });
  }

  /**
   * Which book a pasted page joins.
   *
   * <p>A paste carries no indication of which of the two it belongs to, and damage and
   * salable are claimed and shelved differently — a page filed under the wrong one looks
   * perfectly correct afterwards. So the target is always shown before the paste
   * happens, and clicking either book moves it.
   */
  pasteBook: 'DAMAGE' | 'SALABLE' = 'SALABLE';

  // ── Narrowing the list to one business ──────────────────────────────────
  // The lorry is unloaded one business at a time — the Rainco boxes, then the
  // stationery — so the count is checked the same way. Clicking the line in the
  // breakdown shows just those bills.

  bizFilter: string | null = null;

  toggleBizFilter(business: string): void {
    this.bizFilter = this.bizFilter === business ? null : business;
    this.cdr.markForCheck();
  }

  clearBizFilter(): void {
    this.bizFilter = null;
    this.cdr.markForCheck();
  }

  /** The run's bills, narrowed to the chosen business. */
  visibleBills(run: any): any[] {
    const bills = run?.bills ?? [];
    return this.bizFilter ? bills.filter((b: any) => b.business === this.bizFilter) : bills;
  }

  focusBook(type: 'DAMAGE' | 'SALABLE'): void {
    this.pasteBook = type;
    this.cdr.markForCheck();
  }

  /**
   * Ctrl+V anywhere on the page, into whichever book is aimed at.
   *
   * <p>On the document rather than a book, because a paste only reaches the focused
   * element and neither book is focusable. Several images pasted in turn all join the
   * same book, which is what writing up one book of pages actually looks like.
   */
  @HostListener('document:paste', ['$event'])
  onPaste(e: ClipboardEvent): void {
    if (!this.selected) return;
    const items = e.clipboardData?.items;
    if (!items) return;

    const files: File[] = [];
    for (const item of Array.from(items)) {
      if (item.kind !== 'file' || !item.type.startsWith('image/')) continue;
      const file = item.getAsFile();
      if (file) files.push(file);
    }
    if (!files.length) return;
    e.preventDefault();
    this.uploadPages(files, this.pasteBook);
  }

  onPagePicked(e: Event, type: 'DAMAGE' | 'SALABLE'): void {
    const input = e.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    this.uploadPages(files, type);
  }

  /** One path for picked files and pasted screenshots alike. */
  private uploadPages(files: File[], type: 'DAMAGE' | 'SALABLE'): void {
    if (!files.length || !this.selected) return;
    this.pasteBook = type;

    this.pageError = '';
    this.uploadingPage = type;
    this.cdr.markForCheck();

    const runId = this.selected.id;
    // Numbered from what is already there, so page 2 follows page 1 without asking.
    let next = (this.runImages[type]?.length ?? 0) + 1;

    // Several pages can be picked at once; they upload one after another so the
    // numbering stays in the order they were chosen.
    const uploadNext = (i: number): void => {
      if (i >= files.length) {
        this.uploadingPage = null;
        this.loadRunImages(runId);
        return;
      }
      compressImage(files[i]).then(result => {
        this.returns.uploadImage(result.file, type).subscribe({
          next: (url) => {
            this.returns.addRunImage(runId, type, url, next++).subscribe({
              next: () => uploadNext(i + 1),
              error: () => { this.pageError = 'Could not attach the page.'; this.uploadingPage = null; this.cdr.markForCheck(); },
            });
          },
          error: () => {
            this.pageError = 'Upload failed — check the connection.';
            this.uploadingPage = null;
            this.cdr.markForCheck();
          },
        });
      });
    };
    uploadNext(0);
  }

  deletePage(img: ReturnImage): void {
    this.dialog.open(ConfirmDialog, {
      data: {
        title: 'Remove this page',
        message: 'The photo is deleted. Any return already confirmed against it stays confirmed.',
        confirmText: 'Remove',
        confirmColor: 'warn',
      },
    }).afterClosed().subscribe(r => {
      if (!r?.confirmed || !this.selected) return;
      const runId = this.selected.id;
      this.returns.deleteImage(img.id).subscribe({
        next: () => this.loadRunImages(runId),
        error: () => this.snack.open('Failed.', 'OK', { duration: 4000 }),
      });
    });
  }

  openImage(url: string): void { window.open(url, '_blank', 'noopener'); }

  // ── Helpers ──────────────────────────────────────────────────────

  statusLabel(s: string): string {
    switch (s) {
      case 'OPEN':       return 'Open — bills still being added';
      case 'DISPATCHED': return 'Dispatched';
      case 'COMPLETED':  return 'Completed';
      case 'CANCELLED':  return 'Cancelled';
      default:           return s;
    }
  }

  /**
   * The round split by business.
   *
   * <p>One lorry carries all three, but they are reconciled separately — the Rainco
   * count is checked against a Rainco load. A single total for the trip cannot answer
   * "how many Rainco bills went out", which is the question actually asked when the
   * stock is signed off.
   *
   * <p>Ordered Rainco, Stationery, Plastic — the order the loads are counted in —
   * with anything else after.
   */
  breakdown(run: DeliveryRun): { business: string; bills: number; value: number; owed: number }[] {
    const order = ['RAINCO', 'STATIONERY', 'PLASTIC'];
    const map = new Map<string, { business: string; bills: number; value: number; owed: number }>();

    for (const b of run.bills ?? []) {
      const key = b.business ?? '—';
      const row = map.get(key) ?? { business: key, bills: 0, value: 0, owed: 0 };
      row.bills += 1;
      row.value += Number(b.totalAmount) || 0;
      row.owed  += Number(b.balanceRemaining) || 0;
      map.set(key, row);
    }

    return [...map.values()].sort((a, b) => {
      const ia = order.indexOf(a.business);
      const ib = order.indexOf(b.business);
      if (ia !== -1 && ib !== -1) return ia - ib;
      if (ia !== -1) return -1;
      if (ib !== -1) return 1;
      return a.business.localeCompare(b.business);
    });
  }

  /** Unpaid value on the round — what the collection trip has to bring back. */
  outstandingOf(run: DeliveryRun): number {
    return (run.bills ?? []).reduce((s, b) => s + (Number(b.balanceRemaining) || 0), 0);
  }

  private monthsAgo(n: number): Date {
    const d = new Date(); d.setMonth(d.getMonth() - n); return d;
  }

  private monthsAhead(n: number): Date {
    const d = new Date(); d.setMonth(d.getMonth() + n); return d;
  }
}
