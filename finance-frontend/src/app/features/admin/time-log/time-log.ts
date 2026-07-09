import { CommonModule, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { forkJoin } from 'rxjs';
import { Worker as WorkerApi, WorkerResponse } from '../../../core/services/worker';
import {
  TimeLogService, WorkerAverageHours, WorkerAttendanceRecord,
  CompanyHoliday, WorkerTimeEntry,
} from '../../../core/services/time-log';
import { localDateStr } from '../../../core/utils/date-utils';

interface TimeEntryDraft {
  clockIn: string;
  clockOut: string;
  notes: string;
  saving: boolean;
  error: string;
}

@Component({
  selector: 'app-time-log',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule, FormsModule, DecimalPipe,
    MatTabsModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatCheckboxModule, MatTooltipModule,
  ],
  templateUrl: './time-log.html',
  styleUrl: './time-log.scss',
})
export class TimeLogPage implements OnInit {

  allWorkers: WorkerResponse[] = [];    // all active workers (for tab 4 participants)
  workers: WorkerResponse[] = [];       // time_log_active=true workers (for attendance tab)
  participantTogglingId: number | null = null;

  // ── Average tab ─────────────────────────────────────────────────────
  avgFrom = this.firstDayOfMonth();
  avgTo   = localDateStr();
  avgData: WorkerAverageHours[] = [];
  avgLoading = false;
  avgError = '';

  // ── Attendance + Time Entries tab ────────────────────────────────────
  attDate = localDateStr();
  attRecords: WorkerAttendanceRecord[] = [];
  attLoading = false;
  attError = '';

  // Pre-computed maps for O(1) template access
  savedTypeMap:    Map<number, string>  = new Map();  // workerId → saved attendanceType
  attDrafts:       Map<number, string>  = new Map();  // workerId → selected type in UI
  attSaving:       Map<number, boolean> = new Map();
  timeEntries:     Map<number, WorkerTimeEntry[]> = new Map();
  totalMinsMap:    Map<number, number>  = new Map();  // workerId → total worked minutes today
  newEntryDrafts:  Map<number, TimeEntryDraft>    = new Map();
  showNewEntry:    Map<number, boolean> = new Map();
  editingEntry:    Map<number, TimeEntryDraft>    = new Map(); // entryId → draft

  // ── Holidays tab ─────────────────────────────────────────────────────
  holFrom = this.firstDayOfMonth();
  holTo   = this.lastDayOfMonth();
  holidays: CompanyHoliday[] = [];
  holLoading = false;
  holError = '';

  newHolDate   = localDateStr();
  newHolName   = '';
  newHolType   = 'POYA';
  newHolAll    = true;
  newHolWorkers: number[] = [];
  holSaving    = false;
  holSaveError = '';

  readonly holTypes = [
    { value: 'POYA',     label: 'Poya Day' },
    { value: 'NATIONAL', label: 'National Holiday' },
    { value: 'PUBLIC',   label: 'Public Holiday' },
    { value: 'OTHER',    label: 'Other' },
  ];

  readonly attTypes = [
    { value: 'PRESENT',  label: 'Present',       icon: 'check_circle',  color: '#43a047' },
    { value: 'HALF_DAY', label: 'Half Day',       icon: 'access_time',   color: '#ff9800' },
    { value: 'ABSENT',   label: 'Absent',         icon: 'cancel',        color: '#ef5350' },
    { value: 'HOLIDAY',  label: 'Informed Leave', icon: 'event_available', color: '#7b1fa2' },
  ];

  // ── Monthly View tab ─────────────────────────────────────────────────
  mvMonth    = new Date().toISOString().slice(0, 7);  // "2026-07"
  mvWorkerId: number | null = null;
  mvRecords: WorkerAttendanceRecord[] = [];
  mvLoading  = false;
  mvError    = '';

  get mvDays(): string[] {
    if (!this.mvMonth) return [];
    const [y, m] = this.mvMonth.split('-').map(Number);
    const count  = new Date(y, m, 0).getDate();
    return Array.from({ length: count }, (_, i) =>
      `${y}-${String(m).padStart(2, '0')}-${String(i + 1).padStart(2, '0')}`
    );
  }

  get mvDayMap(): Map<string, WorkerAttendanceRecord> {
    const map = new Map<string, WorkerAttendanceRecord>();
    this.mvRecords.forEach(r => map.set(r.date, r));
    return map;
  }

  get mvTotalPresent(): number { return this.mvRecords.filter(r => r.attendanceType === 'PRESENT').length; }
  get mvTotalHalf():    number { return this.mvRecords.filter(r => r.attendanceType === 'HALF_DAY').length; }
  get mvTotalAbsent():  number { return this.mvRecords.filter(r => r.attendanceType === 'ABSENT').length; }
  get mvTotalHoliday(): number { return this.mvRecords.filter(r => r.attendanceType === 'HOLIDAY').length; }
  get mvTotalHours():   number {
    return this.mvRecords.reduce((s, r) => s + (r.hoursWorked ?? 0), 0);
  }

  get mvSelectedWorker(): WorkerResponse | null {
    return this.allWorkers.find(w => w.id === this.mvWorkerId) ?? null;
  }

  // ── Monthly View inline editing ──────────────────────────────────────
  mvEditingDate: string | null = null;
  mvEditType     = 'PRESENT';
  mvEditHours: number | null   = null;
  mvEditSaving   = false;
  mvEditError    = '';

  mvStartEdit(date: string): void {
    const existing = this.mvDayMap.get(date);
    this.mvEditingDate = date;
    this.mvEditType    = existing?.attendanceType ?? 'PRESENT';
    this.mvEditHours   = existing?.hoursWorked   ?? (this.mvSelectedWorker?.normalWorkingHours ?? 8);
    this.mvEditError   = '';
    this.cdr.detectChanges();
  }

  mvCancelEdit(): void {
    this.mvEditingDate = null;
    this.mvEditError   = '';
    this.cdr.detectChanges();
  }

  mvOnTypeChange(): void {
    if (this.mvEditType === 'ABSENT' || this.mvEditType === 'HOLIDAY') {
      this.mvEditHours = 0;
    } else if (!this.mvEditHours) {
      this.mvEditHours = this.mvSelectedWorker?.normalWorkingHours ?? 8;
    }
    this.cdr.detectChanges();
  }

  mvSaveDay(): void {
    if (!this.mvWorkerId || !this.mvEditingDate) return;
    this.mvEditSaving = true;
    this.mvEditError  = '';
    this.cdr.detectChanges();

    const date      = this.mvEditingDate;
    const workerId  = this.mvWorkerId;
    const attType   = this.mvEditType;
    const hours     = this.mvEditHours ?? 0;

    this.timeLog.recordAttendance({ workerId, date, attendanceType: attType }).subscribe({
      next: rec => {
        const idx = this.mvRecords.findIndex(r => r.date === date);
        const updated = { ...rec, hoursWorked: hours > 0 ? hours : (rec.hoursWorked ?? null) };
        if (idx >= 0) this.mvRecords[idx] = updated; else this.mvRecords.push(updated);
        this.mvRecords = [...this.mvRecords];

        // Add time entry for worked hours on PRESENT / HALF_DAY
        if ((attType === 'PRESENT' || attType === 'HALF_DAY') && hours > 0) {
          const clockIn  = '08:00';
          const clockOut = this.addHoursToTime(clockIn, hours);
          this.timeLog.addTimeEntry({ workerId, date, clockIn, clockOut, notes: null }).subscribe({
            next: entry => {
              // Update hoursWorked in the record
              const i = this.mvRecords.findIndex(r => r.date === date);
              if (i >= 0) this.mvRecords[i] = { ...this.mvRecords[i], hoursWorked: entry.workedMinutes / 60 };
              this.mvRecords = [...this.mvRecords];
              this.mvEditSaving  = false;
              this.mvEditingDate = null;
              this.cdr.detectChanges();
            },
            error: () => {
              this.mvEditSaving  = false;
              this.mvEditingDate = null;
              this.cdr.detectChanges();
            },
          });
        } else {
          this.mvEditSaving  = false;
          this.mvEditingDate = null;
          this.cdr.detectChanges();
        }
      },
      error: e => {
        this.mvEditError  = e?.error?.message ?? 'Failed to save.';
        this.mvEditSaving = false;
        this.cdr.detectChanges();
      },
    });
  }

  private addHoursToTime(time: string, hours: number): string {
    const [h, m] = time.split(':').map(Number);
    const total  = h * 60 + m + Math.round(hours * 60);
    return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
  }

  loadMonthlyView(): void {
    if (!this.mvMonth || !this.mvWorkerId) return;
    const [y, m] = this.mvMonth.split('-').map(Number);
    const from   = `${y}-${String(m).padStart(2, '0')}-01`;
    const lastDay = new Date(y, m, 0).getDate();
    const to     = `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
    this.mvLoading = true;
    this.mvError   = '';
    this.cdr.detectChanges();
    this.timeLog.getWorkerAttendance(this.mvWorkerId, from, to).subscribe({
      next: recs  => { this.mvRecords = recs; this.mvLoading = false; this.cdr.detectChanges(); },
      error: ()   => { this.mvError = 'Failed to load.'; this.mvLoading = false; this.cdr.detectChanges(); },
    });
  }

  mvDayName(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('en-US', { weekday: 'short' });
  }

  mvAttColor(type: string): string {
    return ({ PRESENT: '#43a047', HALF_DAY: '#ff9800', ABSENT: '#ef5350', HOLIDAY: '#7b1fa2' } as Record<string, string>)[type] ?? '#888';
  }

  constructor(
    private timeLog: TimeLogService,
    private workerApi: WorkerApi,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadWorkers();
    this.loadAverage();
    this.loadHolidays();
  }

  // trackBy functions prevent full list re-render on Map changes
  trackByWorker(_: number, w: WorkerResponse): number { return w.id; }
  trackByEntry(_: number, e: WorkerTimeEntry): number  { return e.id; }

  private loadWorkers(): void {
    this.workerApi.getAllWorkers().subscribe({
      next: w => {
        this.allWorkers = w.filter(x => x.active);
        this.workers    = this.allWorkers.filter(x => x.timeLogActive);
        this.loadAttendanceAndEntries();
        this.cdr.detectChanges();
      },
      error: () => {},
    });
  }

  // ── Participants tab ─────────────────────────────────────────────────

  toggleParticipant(worker: WorkerResponse): void {
    if (this.participantTogglingId === worker.id) return;
    this.participantTogglingId = worker.id;
    this.cdr.detectChanges();
    this.timeLog.toggleWorkerTimeLog(worker.id).subscribe({
      next: updated => {
        const idx = this.allWorkers.findIndex(w => w.id === updated.id);
        if (idx >= 0) this.allWorkers[idx] = updated;
        this.workers = this.allWorkers.filter(x => x.timeLogActive);
        this.participantTogglingId = null;
        this.cdr.detectChanges();
      },
      error: () => { this.participantTogglingId = null; this.cdr.detectChanges(); },
    });
  }

  isTogglingParticipant(id: number): boolean { return this.participantTogglingId === id; }

  workerTypeLabel(type: string): string {
    const map: Record<string, string> = {
      DELIVERY: 'Delivery', SALES_REP: 'Sales Rep', SHOP: 'Shop',
      ACCOUNTANT: 'Accountant', MAIN_ACCOUNTANT: 'Main Accountant',
      DRIVER: 'Driver', OTHER: 'Other',
    };
    return map[type] ?? type;
  }

  // ── Average ──────────────────────────────────────────────────────────

  loadAverage(): void {
    this.avgLoading = true;
    this.avgError   = '';
    this.cdr.detectChanges();
    this.timeLog.getAverageHours(this.avgFrom, this.avgTo).subscribe({
      next: d  => { this.avgData = d; this.avgLoading = false; this.cdr.detectChanges(); },
      error: () => { this.avgError = 'Failed to load.'; this.avgLoading = false; this.cdr.detectChanges(); },
    });
  }

  attendanceRate(w: WorkerAverageHours): number {
    if (!w.expectedWorkingDays) return 0;
    return Math.round(((w.presentDays + w.halfDays * 0.5) / w.expectedWorkingDays) * 100);
  }

  rateColor(r: number): string {
    return r >= 90 ? '#43a047' : r >= 70 ? '#ff9800' : '#ef5350';
  }

  formatHours(mins: number): string {
    const h = Math.floor(mins / 60);
    const m = mins % 60;
    return m > 0 ? `${h}h ${m}m` : `${h}h`;
  }

  // ── Attendance + Entries ─────────────────────────────────────────────

  loadAttendanceAndEntries(): void {
    this.attLoading = true;
    this.attError   = '';
    this.attDrafts.clear();
    this.attSaving.clear();
    this.savedTypeMap.clear();
    this.timeEntries.clear();
    this.totalMinsMap.clear();
    this.newEntryDrafts.clear();
    this.showNewEntry.clear();
    this.editingEntry.clear();
    this.cdr.detectChanges();

    // Single render after both calls complete
    forkJoin({
      recs:    this.timeLog.getAttendanceForDate(this.attDate),
      entries: this.timeLog.getEntriesForDate(this.attDate),
    }).subscribe({
      next: ({ recs, entries }) => {
        this.attRecords = recs;

        // Build O(1) lookup maps from records
        recs.forEach(r => {
          this.savedTypeMap.set(r.workerId, r.attendanceType);
          this.attDrafts.set(r.workerId, r.attendanceType);
        });
        // Seed PRESENT for workers without a record yet
        this.workers.forEach(w => {
          if (!this.attDrafts.has(w.id)) this.attDrafts.set(w.id, 'PRESENT');
        });

        // Build entries map + total minutes map
        entries.forEach(e => {
          const list = this.timeEntries.get(e.workerId) ?? [];
          list.push(e);
          this.timeEntries.set(e.workerId, list);
        });
        this.workers.forEach(w => {
          const mins = (this.timeEntries.get(w.id) ?? [])
            .reduce((s, e) => s + (e.workedMinutes ?? 0), 0);
          this.totalMinsMap.set(w.id, mins);
        });

        this.attLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.attError   = 'Failed to load attendance.';
        this.attLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  // O(1) map lookups used in template
  getAttType(workerId: number): string    { return this.attDrafts.get(workerId) ?? 'PRESENT'; }
  getSavedType(workerId: number): string  { return this.savedTypeMap.get(workerId) ?? ''; }
  totalMinutes(workerId: number): number  { return this.totalMinsMap.get(workerId) ?? 0; }
  isAttSaving(id: number): boolean        { return this.attSaving.get(id) ?? false; }
  getEntries(workerId: number): WorkerTimeEntry[] { return this.timeEntries.get(workerId) ?? []; }

  setAttType(workerId: number, type: string): void {
    this.attDrafts.set(workerId, type);
    this.cdr.detectChanges();
  }

  saveAttType(worker: WorkerResponse): void {
    this.attSaving.set(worker.id, true);
    this.cdr.detectChanges();
    this.timeLog.recordAttendance({
      workerId: worker.id,
      date: this.attDate,
      attendanceType: this.getAttType(worker.id),
    }).subscribe({
      next: rec => {
        const idx = this.attRecords.findIndex(r => r.workerId === worker.id);
        if (idx >= 0) this.attRecords[idx] = rec; else this.attRecords.push(rec);
        this.savedTypeMap.set(rec.workerId, rec.attendanceType);
        this.attSaving.set(worker.id, false);
        this.cdr.detectChanges();
      },
      error: () => { this.attSaving.set(worker.id, false); this.cdr.detectChanges(); },
    });
  }

  attTypeInfo(type: string) { return this.attTypes.find(t => t.value === type) ?? this.attTypes[0]; }

  // ── Time entries ─────────────────────────────────────────────────────

  private recomputeTotalMins(workerId: number): void {
    const mins = (this.timeEntries.get(workerId) ?? [])
      .reduce((s, e) => s + (e.workedMinutes ?? 0), 0);
    this.totalMinsMap.set(workerId, mins);
  }

  toggleNewEntry(workerId: number): void {
    const showing = this.showNewEntry.get(workerId) ?? false;
    this.showNewEntry.set(workerId, !showing);
    if (!showing) {
      this.newEntryDrafts.set(workerId, {
        clockIn: this.currentTimeStr(), clockOut: '', notes: '', saving: false, error: '',
      });
    } else {
      this.newEntryDrafts.delete(workerId);
    }
    this.cdr.detectChanges();
  }

  getNewDraft(workerId: number): TimeEntryDraft | null {
    return this.newEntryDrafts.get(workerId) ?? null;
  }

  setNow(workerId: number, field: 'clockIn' | 'clockOut'): void {
    const d = this.newEntryDrafts.get(workerId);
    if (d) { d[field] = this.currentTimeStr(); this.cdr.detectChanges(); }
  }

  saveNewEntry(worker: WorkerResponse): void {
    const d = this.newEntryDrafts.get(worker.id);
    if (!d) return;
    if (!d.clockIn) { d.error = 'Clock-in time is required.'; this.cdr.detectChanges(); return; }
    d.saving = true;
    d.error  = '';
    this.cdr.detectChanges();

    this.timeLog.addTimeEntry({
      workerId: worker.id,
      date:     this.attDate,
      clockIn:  d.clockIn,
      clockOut: d.clockOut || null,
      notes:    d.notes || null,
    }).subscribe({
      next: entry => {
        const list = this.timeEntries.get(worker.id) ?? [];
        list.push(entry);
        this.timeEntries.set(worker.id, list);
        this.recomputeTotalMins(worker.id);
        // Auto-suggest PRESENT if worker was ABSENT/HOLIDAY
        if (['ABSENT', 'HOLIDAY'].includes(this.getAttType(worker.id))) {
          this.attDrafts.set(worker.id, 'PRESENT');
        }
        this.showNewEntry.set(worker.id, false);
        this.newEntryDrafts.delete(worker.id);
        this.cdr.detectChanges();
      },
      error: e => { d.error = e?.error?.message ?? 'Failed to save.'; d.saving = false; this.cdr.detectChanges(); },
    });
  }

  startEditEntry(entry: WorkerTimeEntry): void {
    this.editingEntry.set(entry.id, {
      clockIn:  entry.clockIn.slice(0, 5),
      clockOut: entry.clockOut ? entry.clockOut.slice(0, 5) : '',
      notes:    entry.notes ?? '',
      saving:   false,
      error:    '',
    });
    this.cdr.detectChanges();
  }

  getEditDraft(entryId: number): TimeEntryDraft | null {
    return this.editingEntry.get(entryId) ?? null;
  }

  setEditNow(entryId: number, field: 'clockIn' | 'clockOut'): void {
    const d = this.editingEntry.get(entryId);
    if (d) { d[field] = this.currentTimeStr(); this.cdr.detectChanges(); }
  }

  saveEditEntry(entry: WorkerTimeEntry): void {
    const d = this.editingEntry.get(entry.id);
    if (!d) return;
    if (!d.clockIn) { d.error = 'Clock-in time is required.'; this.cdr.detectChanges(); return; }
    d.saving = true;
    d.error  = '';
    this.cdr.detectChanges();
    this.timeLog.updateTimeEntry(entry.id, {
      workerId: entry.workerId,
      date:     entry.date,
      clockIn:  d.clockIn,
      clockOut: d.clockOut || null,
      notes:    d.notes || null,
    }).subscribe({
      next: updated => {
        const list = this.timeEntries.get(entry.workerId) ?? [];
        const idx  = list.findIndex(e => e.id === entry.id);
        if (idx >= 0) list[idx] = updated;
        this.editingEntry.delete(entry.id);
        this.recomputeTotalMins(entry.workerId);
        this.cdr.detectChanges();
      },
      error: e => { d.error = e?.error?.message ?? 'Failed to update.'; d.saving = false; this.cdr.detectChanges(); },
    });
  }

  cancelEditEntry(entryId: number): void {
    this.editingEntry.delete(entryId);
    this.cdr.detectChanges();
  }

  deleteEntry(entry: WorkerTimeEntry): void {
    this.timeLog.deleteTimeEntry(entry.id).subscribe({
      next: () => {
        const list = this.timeEntries.get(entry.workerId) ?? [];
        this.timeEntries.set(entry.workerId, list.filter(e => e.id !== entry.id));
        this.recomputeTotalMins(entry.workerId);
        this.cdr.detectChanges();
      },
    });
  }

  private currentTimeStr(): string {
    const now = new Date();
    return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  }

  formatTime(t: string | null): string {
    if (!t) return '—';
    return t.slice(0, 5);
  }

  // ── Holidays ─────────────────────────────────────────────────────────

  loadHolidays(): void {
    this.holLoading = true;
    this.cdr.detectChanges();
    this.timeLog.getHolidays(this.holFrom, this.holTo).subscribe({
      next: h  => { this.holidays = h; this.holLoading = false; this.cdr.detectChanges(); },
      error: () => { this.holError = 'Failed.'; this.holLoading = false; this.cdr.detectChanges(); },
    });
  }

  saveHoliday(): void {
    if (!this.newHolName.trim()) return;
    this.holSaving    = true;
    this.holSaveError = '';
    this.cdr.detectChanges();
    this.timeLog.createHoliday({
      date:        this.newHolDate,
      name:        this.newHolName.trim(),
      holidayType: this.newHolType,
      appliesToAll: this.newHolAll,
      workerIds:   this.newHolAll ? [] : [...this.newHolWorkers],
    }).subscribe({
      next: h => {
        this.holidays.push(h);
        this.holidays.sort((a, b) => a.date.localeCompare(b.date));
        this.newHolName    = '';
        this.newHolWorkers = [];
        this.newHolAll     = true;
        this.holSaving     = false;
        this.cdr.detectChanges();
      },
      error: e => {
        this.holSaveError = e?.error?.message ?? 'Failed.';
        this.holSaving    = false;
        this.cdr.detectChanges();
      },
    });
  }

  deleteHoliday(id: number): void {
    this.timeLog.deleteHoliday(id).subscribe({
      next: () => { this.holidays = this.holidays.filter(h => h.id !== id); this.cdr.detectChanges(); },
    });
  }

  toggleHolWorker(id: number): void {
    const i = this.newHolWorkers.indexOf(id);
    if (i >= 0) this.newHolWorkers.splice(i, 1); else this.newHolWorkers.push(id);
    this.cdr.detectChanges();
  }

  isHolWorkerSelected(id: number): boolean { return this.newHolWorkers.includes(id); }
  holTypeLabel(t: string): string { return this.holTypes.find(x => x.value === t)?.label ?? t; }
  holTypeColor(t: string): string {
    return ({ POYA: '#7b1fa2', NATIONAL: '#1976d2', PUBLIC: '#43a047', OTHER: '#757575' } as Record<string, string>)[t] ?? '#757575';
  }

  private firstDayOfMonth(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`;
  }
  private lastDayOfMonth(): string {
    const d = new Date();
    return localDateStr(new Date(d.getFullYear(), d.getMonth() + 1, 0));
  }
}
