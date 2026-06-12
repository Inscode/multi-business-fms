import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component,
  HostListener, OnInit,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin, Observable, of } from 'rxjs';
import { switchMap } from 'rxjs/operators';

import { Auth } from '../../../core/services/auth';
import { TaskService } from '../services/task.service';
import { TaskInstance, Clarification, TaskTemplate } from '../models/task.models';
import {
  CreateTemplateDialog,
  CreateTemplateDialogData,
} from './create-template-dialog/create-template-dialog';

@Component({
  selector: 'app-task-board',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    MatTabsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatChipsModule,
    MatTableModule,
    MatMenuModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
    DatePipe,
  ],
  templateUrl: './task-board.html',
  styleUrl:    './task-board.scss',
})
export class TaskBoard implements OnInit {

  // ── Data ──────────────────────────────────────────────────────────────────
  instances:       TaskInstance[]               = [];
  clarMap:         Map<number, Clarification[]> = new Map();
  templates:       TaskTemplate[]               = [];
  allTabInstances: TaskInstance[]               = [];
  allTabDate:      string = (() => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; })();
  allTabDateObj:   Date   = new Date();
  loading        = false;
  allTabLoading  = false;

  // ── Inline panel state ───────────────────────────────────────────────────
  openPanel     = new Map<number, 'cannot' | 'question' | 'image'>();
  closedThreads = new Set<number>(); // tracks panels the user explicitly closed
  cannotReasons = new Map<number, string>();
  questionTexts = new Map<number, string>();
  replyTexts    = new Map<number, string>();
  pasteFiles    = new Map<number, File>();
  pastePreviews = new Map<number, string>();
  questionFiles    = new Map<number, File>();
  questionPreviews = new Map<number, string>();
  replyFiles       = new Map<number, File[]>();
  replyPreviews    = new Map<number, string[]>();
  activeReplyId:   number | null = null;
  submitting = new Map<number, boolean>();

  // ── Lightbox ─────────────────────────────────────────────────────────────
  lightboxUrl: string | null = null;

  // ── Moved tab reassign ───────────────────────────────────────────────────
  reassigningId: number | null = null;
  reassignDate:  Date | null   = null;

  readonly typeIconMap: Record<string, { icon: string; bg: string }> = {
    EXPENSE:        { icon: 'account_balance_wallet', bg: '#fb923c' },
    STOCK:          { icon: 'inventory',              bg: '#60a5fa' },
    ATTENDANCE:     { icon: 'people',                 bg: '#818cf8' },
    BILLS:          { icon: 'receipt_long',            bg: '#34d399' },
    DEDUCTION:      { icon: 'money_off',              bg: '#f87171' },
    UPLOAD:         { icon: 'cloud_upload',           bg: '#c084fc' },
    DELIVERY_BILLS: { icon: 'local_shipping',         bg: '#38bdf8' },
    PAYMENTS:       { icon: 'payments',               bg: '#4ade80' },
  };

  readonly typeLabelMap: Record<string, string> = {
    EXPENSE: 'Expense', STOCK: 'Stock entry', ATTENDANCE: 'Attendance',
    BILLS: 'Bills', DEDUCTION: 'Deduction', UPLOAD: 'Upload',
    DELIVERY_BILLS: 'Delivery bills', PAYMENTS: 'Payments',
  };

  readonly roleLabelMap: Record<string, string> = {
    MAIN_ACCOUNTANT: 'Main accountant',
    ACCOUNTANT:      'Accountant',
    DELIVERY:        'Delivery',
    ADMIN:           'Admin',
    OWNER:           'Owner',
  };

  allColumns = ['title', 'assignedTo', 'status', 'completedAt', 'movedReason', 'actions'];

  readonly freqLabelMap: Record<string, string> = {
    DAILY:     'Daily',
    ON_DEMAND: 'On demand',
    ONE_TIME:  'One-time',
  };

  freqLabel(freq: string): string { return this.freqLabelMap[freq] ?? freq; }

  constructor(
    private auth:        Auth,
    private taskService: TaskService,
    private dialog:      MatDialog,
    private snackBar:    MatSnackBar,
    readonly cdr:        ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.loadInstances();
    this.loadTemplates();
    this.loadAllTab();
  }

  // ── Role helpers ──────────────────────────────────────────────────────────

  get role(): string { return this.auth.getRole() ?? ''; }

  get isAdminOrOwner(): boolean {
    return ['ADMIN', 'OWNER'].includes(this.role);
  }

  get canReply(): boolean {
    return ['OWNER', 'ADMIN', 'MAIN_ACCOUNTANT'].includes(this.role);
  }

  // ── Computed lists ────────────────────────────────────────────────────────

  private get today(): string { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`; }

  get todayInstances(): TaskInstance[] {
    const all = this.instances.filter(i => i.date === this.today);
    if (this.isAdminOrOwner) return all;
    const allowed: Record<string, string> = {
      MAIN_ACCOUNTANT: 'MAIN_ACCOUNTANT',
      ACCOUNTANT:      'ACCOUNTANT',
      DELIVERY:        'DELIVERY',
    };
    const myRole = allowed[this.role];
    return myRole ? all.filter(i => i.template.assignedRole === myRole) : all;
  }

  get dailyInstances():    TaskInstance[] { return this.todayInstances.filter(i => i.template.frequency === 'DAILY'); }
  get onDemandInstances(): TaskInstance[] { return this.todayInstances.filter(i => i.template.frequency === 'ON_DEMAND'); }
  get oneTimeInstances():  TaskInstance[] { return this.todayInstances.filter(i => i.template.frequency === 'ONE_TIME'); }

  get movedInstances(): TaskInstance[] {
    return this.instances.filter(i => i.status === 'MOVED');
  }

  get movedGrouped(): { date: string; instances: TaskInstance[] }[] {
    const map = new Map<string, TaskInstance[]>();
    for (const inst of this.movedInstances) {
      const key = inst.movedToDate ?? 'Unscheduled';
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(inst);
    }
    return Array.from(map.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, instances]) => ({ date, instances }));
  }

  get clarificationInstances(): TaskInstance[] {
    return this.instances.filter(i => {
      const clars = this.clarMap.get(i.id) ?? [];
      return clars.some(c => c.type === 'QUESTION') && !clars.some(c => c.type === 'REPLY');
    });
  }

  get pendingTodayCount(): number {
    return this.todayInstances.filter(i =>
      i.status === 'PENDING' || i.status === 'AWAITING_CLARIFICATION',
    ).length;
  }
  get movedCount():         number { return this.movedInstances.length; }
  get clarificationCount(): number { return this.clarificationInstances.length; }

  get dailyTemplates():    TaskTemplate[] { return this.templates.filter(t => t.frequency === 'DAILY'); }
  get onDemandTemplates(): TaskTemplate[] { return this.templates.filter(t => t.frequency === 'ON_DEMAND'); }

  // All-tasks view (grouped by frequency for the admin date-view tab)
  get allTabDaily():    TaskInstance[] { return this.allTabInstances.filter(i => i.template.frequency === 'DAILY'); }
  get allTabOnDemand(): TaskInstance[] { return this.allTabInstances.filter(i => i.template.frequency === 'ON_DEMAND'); }
  get allTabOneTime():  TaskInstance[] { return this.allTabInstances.filter(i => i.template.frequency === 'ONE_TIME'); }

  // ── Data loading ──────────────────────────────────────────────────────────

  // ── Escape closes lightbox ────────────────────────────────────────────────
  @HostListener('document:keydown.escape')
  onEscape(): void { this.closeLightbox(); }

  // ── Lightbox ──────────────────────────────────────────────────────────────
  openLightbox(url: string): void  { this.lightboxUrl = url; this.cdr.markForCheck(); }
  closeLightbox(): void            { this.lightboxUrl = null; this.cdr.markForCheck(); }

  private loadInstances(): void {
    this.loading = true;
    this.taskService.getAllInstances().subscribe({
      next: list => {
        this.instances = list;
        this.loading = false;
        const awaitingIds = list.filter(i => i.status === 'AWAITING_CLARIFICATION');
        awaitingIds.forEach(i => this.ensureClarifications(i.id));
        this.notifyOnLoad(awaitingIds.length);
        // For non-admin roles: also load clarifications for pending tasks so we
        // can detect replies the admin has sent back
        if (!this.isAdminOrOwner) {
          list.filter(i => i.status === 'PENDING').forEach(i => this.ensureClarifications(i.id));
          // after a tick, check for answered questions
          setTimeout(() => this.notifyRepliesReceived(), 600);
        }
        this.cdr.markForCheck();
      },
      error: () => { this.loading = false; this.cdr.markForCheck(); },
    });
  }

  private notifyOnLoad(awaitingCount: number): void {
    if (this.isAdminOrOwner || this.role === 'MAIN_ACCOUNTANT') {
      if (awaitingCount > 0) {
        this.snackBar.open(
          `${awaitingCount} task${awaitingCount > 1 ? 's' : ''} waiting for your reply`,
          'View',
          { duration: 6000, panelClass: 'snack-warn' },
        );
      }
    }
  }

  private notifyRepliesReceived(): void {
    const replied = this.instances.filter(i => {
      const clars = this.clarMap.get(i.id) ?? [];
      return clars.length > 0 && clars[clars.length - 1].type === 'REPLY';
    });
    if (replied.length > 0) {
      this.snackBar.open(
        `Admin replied to ${replied.length} task${replied.length > 1 ? 's' : ''}`,
        'View',
        { duration: 6000, panelClass: 'snack-info' },
      );
    }
  }

  private loadTemplates(): void {
    this.taskService.getTemplates().subscribe({
      next: list => { this.templates = list; this.cdr.markForCheck(); },
    });
  }

  loadAllTab(date?: string): void {
    const d = date ?? this.allTabDate;
    this.allTabLoading = true;
    this.allTabInstances = [];        // clear immediately — no stale cards shown
    this.cdr.markForCheck();
    this.taskService.getInstancesForDate(d).subscribe({
      next: list => {
        this.allTabInstances = list;
        this.allTabLoading   = false;
        this.cdr.markForCheck();
      },
      error: () => { this.allTabLoading = false; this.cdr.markForCheck(); },
    });
  }

  onAllTabDateChange(e: { value: Date | null }): void {
    if (!e.value) return;
    this.allTabDate = this.formatDateStr(e.value);
    this.loadAllTab(this.allTabDate);
  }

  private ensureClarifications(id: number): void {
    if (this.clarMap.has(id)) return;
    this.taskService.loadClarifications(id).subscribe({
      next: list => { this.clarMap.set(id, list); this.cdr.markForCheck(); },
    });
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  markDone(inst: TaskInstance): void {
    this.taskService.updateStatus(inst.id, 'COMPLETED').subscribe({
      next: updated => {
        Object.assign(inst, updated);
        this.closePanel(inst.id);
        this.cdr.markForCheck();
      },
    });
  }

  undoStatus(inst: TaskInstance): void {
    this.taskService.updateStatus(inst.id, 'PENDING').subscribe({
      next: updated => {
        Object.assign(inst, updated);
        this.cdr.markForCheck();
      },
    });
  }

  openCannotPanel(inst: TaskInstance): void {
    this.openPanel.set(inst.id, 'cannot');
    if (!this.cannotReasons.has(inst.id)) this.cannotReasons.set(inst.id, '');
    this.cdr.markForCheck();
  }

  setCannotReason(id: number, val: string): void { this.cannotReasons.set(id, val); }

  submitCannot(inst: TaskInstance): void {
    const reason = (this.cannotReasons.get(inst.id) ?? '').trim();
    if (!reason) return;
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const toDate = this.formatDateStr(tomorrow);
    this.taskService.updateStatus(inst.id, 'MOVED', reason, toDate).subscribe({
      next: updated => {
        Object.assign(inst, updated);
        this.closePanel(inst.id);
        this.cdr.markForCheck();
      },
    });
  }

  toggleQuestionPanel(inst: TaskInstance): void {
    if (this.showClarPanel(inst)) {
      this.closePanel(inst.id);
    } else {
      this.closedThreads.delete(inst.id);
      this.openPanel.set(inst.id, 'question');
      this.ensureClarifications(inst.id);
      if (!this.questionTexts.has(inst.id)) this.questionTexts.set(inst.id, '');
      if (!this.replyTexts.has(inst.id))    this.replyTexts.set(inst.id, '');
    }
    this.cdr.markForCheck();
  }

  setQuestionText(id: number, val: string): void { this.questionTexts.set(id, val); }
  setReplyText(id: number, val: string):    void { this.replyTexts.set(id, val); }

  submitQuestion(inst: TaskInstance): void {
    const text = (this.questionTexts.get(inst.id) ?? '').trim();
    if (!text) return;
    if (this.submitting.get(inst.id)) return;
    this.submitting.set(inst.id, true);

    const qFile = this.questionFiles.get(inst.id);
    this.uploadThenClarify(qFile, inst.id, text, 'QUESTION').subscribe({
      next: (clar: Clarification) => {
        this.clarMap.set(inst.id, [...(this.clarMap.get(inst.id) ?? []), clar]);
        inst.status = 'AWAITING_CLARIFICATION';
        this.questionTexts.set(inst.id, '');
        this.questionFiles.delete(inst.id);
        this.questionPreviews.delete(inst.id);
        this.submitting.delete(inst.id);
        this.snackBar.open('Question sent — admin will be notified', '✕', { duration: 4000, panelClass: 'snack-info' });
        this.cdr.markForCheck();
      },
      error: () => {
        this.submitting.delete(inst.id);
        this.snackBar.open('Failed to send question', '✕', { duration: 4000, panelClass: 'snack-error' });
        this.cdr.markForCheck();
      },
    });
  }

  submitReply(inst: TaskInstance): void {
    const text  = (this.replyTexts.get(inst.id) ?? '').trim();
    const files = this.replyFiles.get(inst.id) ?? [];
    if (!text && !files.length) return;
    if (this.submitting.get(inst.id)) return;
    this.submitting.set(inst.id, true);

    this.uploadThenClarify(files, inst.id, text, 'REPLY').subscribe({
      next: (clar: Clarification) => {
        this.clarMap.set(inst.id, [...(this.clarMap.get(inst.id) ?? []), clar]);
        inst.status = 'PENDING';
        this.replyTexts.set(inst.id, '');
        this.replyFiles.delete(inst.id);
        this.replyPreviews.delete(inst.id);
        this.submitting.delete(inst.id);
        const assignedLabel = this.roleLabel(inst.template.assignedRole);
        this.snackBar.open(`Reply sent — ${assignedLabel} has been notified`, '✕', { duration: 4000, panelClass: 'snack-success' });
        this.cdr.markForCheck();
      },
      error: () => {
        this.submitting.delete(inst.id);
        this.snackBar.open('Failed to send reply', '✕', { duration: 4000, panelClass: 'snack-error' });
        this.cdr.markForCheck();
      },
    });
  }

  private uploadThenClarify(
    files: File | File[] | undefined,
    instanceId: number,
    message: string,
    type: 'QUESTION' | 'REPLY',
  ): Observable<Clarification> {
    const fileArr: File[] = !files ? [] : Array.isArray(files) ? files : [files];
    const uploads$: Observable<string[]> = fileArr.length
      ? forkJoin(fileArr.map(f => this.taskService.uploadImage(f)))
      : of([]);
    return uploads$.pipe(
      switchMap((urls: string[]) =>
        this.taskService.addClarification(instanceId, message, type, this.role, urls),
      ),
    );
  }

  onReplyImageSelected(e: Event, inst: TaskInstance): void {
    const files = (e.target as HTMLInputElement).files;
    if (!files) return;
    Array.from(files).forEach(f => this.setReplyFile(inst.id, f));
    (e.target as HTMLInputElement).value = '';
  }

  onReplyFileDrop(e: DragEvent, inst: TaskInstance): void {
    e.preventDefault();
    const files = e.dataTransfer?.files;
    if (!files) return;
    Array.from(files).filter(f => f.type.startsWith('image/')).forEach(f => this.setReplyFile(inst.id, f));
  }

  removeReplyImage(id: number, index: number): void {
    const previews = this.replyPreviews.get(id) ?? [];
    const files    = this.replyFiles.get(id)    ?? [];
    previews.splice(index, 1);
    files.splice(index, 1);
    this.replyPreviews.set(id, [...previews]);
    this.replyFiles.set(id, [...files]);
    this.cdr.markForCheck();
  }

  hasReplyImage(id: number): boolean     { return (this.replyPreviews.get(id) ?? []).length > 0; }
  getReplyPreviews(id: number): string[] { return this.replyPreviews.get(id) ?? []; }

  toggleImagePanel(inst: TaskInstance): void {
    if (this.openPanel.get(inst.id) === 'image') {
      this.closePanel(inst.id);
    } else {
      this.openPanel.set(inst.id, 'image');
    }
    this.cdr.markForCheck();
  }

  closePanel(id: number): void {
    this.openPanel.delete(id);
    this.closedThreads.add(id);
    this.cdr.markForCheck();
  }

  // ── Image / paste zone ────────────────────────────────────────────────────

  @HostListener('document:paste', ['$event'])
  onGlobalPaste(e: ClipboardEvent): void {
    const items = e.clipboardData?.items;
    if (!items) return;
    let imageFile: File | null = null;
    for (let i = 0; i < items.length; i++) {
      if (items[i].type.startsWith('image/')) {
        imageFile = items[i].getAsFile();
        break;
      }
    }
    if (!imageFile) return;

    if (this.activeReplyId != null) {
      this.setReplyFile(this.activeReplyId, imageFile);
      return;
    }
    const pasteInstId = [...this.openPanel.entries()]
      .find(([, v]) => v === 'image')?.[0];
    if (pasteInstId != null) this.setPasteFile(pasteInstId, imageFile);
  }

  setActiveReply(id: number | null): void { this.activeReplyId = id; }

  private setReplyFile(id: number, file: File): void {
    const files    = this.replyFiles.get(id)    ?? [];
    const previews = this.replyPreviews.get(id) ?? [];
    files.push(file);
    this.replyFiles.set(id, files);
    const reader = new FileReader();
    reader.onload = ev => {
      previews.push(ev.target?.result as string);
      this.replyPreviews.set(id, [...previews]);
      this.cdr.markForCheck();
    };
    reader.readAsDataURL(file);
  }

  onFileDrop(e: DragEvent, inst: TaskInstance): void {
    e.preventDefault();
    const file = e.dataTransfer?.files?.[0];
    if (file && file.type.startsWith('image/')) this.setPasteFile(inst.id, file);
  }

  onFileSelected(e: Event, inst: TaskInstance): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) this.setPasteFile(inst.id, file);
  }

  onQuestionImageSelected(e: Event, inst: TaskInstance): void {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.questionFiles.set(inst.id, file);
    const reader = new FileReader();
    reader.onload = ev => {
      this.questionPreviews.set(inst.id, ev.target?.result as string);
      this.cdr.markForCheck();
    };
    reader.readAsDataURL(file);
  }

  private setPasteFile(id: number, file: File): void {
    this.pasteFiles.set(id, file);
    const reader = new FileReader();
    reader.onload = ev => {
      this.pastePreviews.set(id, ev.target?.result as string);
      this.cdr.markForCheck();
    };
    reader.readAsDataURL(file);
    this.cdr.markForCheck();
  }

  clearPasteFile(id: number): void {
    this.pasteFiles.delete(id);
    this.pastePreviews.delete(id);
    this.cdr.markForCheck();
  }

  uploadAttachment(inst: TaskInstance): void {
    const file = this.pasteFiles.get(inst.id);
    if (!file) return;
    this.taskService.uploadImage(file).pipe(
      switchMap(url => this.taskService.addAttachment(inst.id, url)),
    ).subscribe({
      next: updated => {
        inst.attachmentUrls = updated.attachmentUrls;
        this.clearPasteFile(inst.id);
        this.cdr.markForCheck();
      },
    });
  }

  // ── Reassign moved tasks ──────────────────────────────────────────────────

  startReassign(inst: TaskInstance): void {
    this.reassigningId = inst.id;
    this.reassignDate  = inst.movedToDate ? new Date(inst.movedToDate + 'T00:00:00') : null;
    this.cdr.markForCheck();
  }

  onReassignDateChange(e: { value: Date | null }): void {
    this.reassignDate = e.value;
  }

  submitReassign(inst: TaskInstance): void {
    if (!this.reassignDate) return;
    const d = this.formatDateStr(this.reassignDate);
    this.taskService.updateStatus(inst.id, 'MOVED', inst.movedReason, d).subscribe({
      next: updated => {
        Object.assign(inst, updated);
        this.reassigningId = null;
        this.reassignDate  = null;
        this.cdr.markForCheck();
      },
    });
  }

  cancelReassign(): void {
    this.reassigningId = null;
    this.reassignDate  = null;
    this.cdr.markForCheck();
  }

  // ── Template management ───────────────────────────────────────────────────

  toggleDailyTemplate(tpl: TaskTemplate): void {
    this.taskService.toggleActive(tpl.id).subscribe({
      next: updated => {
        tpl.active = updated.active;
        const msg = updated.active ? 'Template resumed — will run daily again' : 'Template paused';
        this.snackBar.open(msg, '✕', { duration: 3500 });
        this.cdr.markForCheck();
      },
    });
  }

  toggleOnDemandTemplate(tpl: TaskTemplate): void {
    const action$ = tpl.active
      ? this.taskService.toggleActive(tpl.id)          // deactivate
      : this.taskService.activateOnDemand(tpl.id);     // activate + create today's instance
    action$.subscribe({
      next: updated => {
        tpl.active = updated.active;
        if (updated.active) {
          this.loadInstances();   // refresh so today's new instance appears
          this.snackBar.open('Task activated — added to today\'s work', '✕', { duration: 3500, panelClass: 'snack-success' });
        } else {
          this.snackBar.open('Task deactivated — removed from today', '✕', { duration: 3500 });
        }
        this.cdr.markForCheck();
      },
    });
  }

  trackByTplId(_: number, tpl: TaskTemplate): number { return tpl.id; }

  openCreateTemplateDialog(): void {
    this.dialog.open(CreateTemplateDialog, {
      data: {} as CreateTemplateDialogData,
      width: '560px',
      disableClose: true,
    }).afterClosed().subscribe((payload) => {
      if (!payload) return;
      this.taskService.createTemplate(payload).subscribe({
        next: () => {
          this.loadInstances();
          this.loadTemplates();
          this.cdr.markForCheck();
        },
      });
    });
  }

  openEditTemplateDialog(tpl: TaskTemplate): void {
    this.dialog.open(CreateTemplateDialog, {
      data: { template: tpl } as CreateTemplateDialogData,
      width: '560px',
      disableClose: true,
    }).afterClosed().subscribe((payload) => {
      if (!payload) return;
      this.taskService.updateTemplate(tpl.id, payload).subscribe({
        next: () => {
          this.loadInstances();
          this.loadTemplates();
          this.loadAllTab();
          this.cdr.markForCheck();
        },
      });
    });
  }

  // ── CSV export ────────────────────────────────────────────────────────────

  exportCsv(): void {
    const header = ['Task', 'Assigned to', 'Status', 'Completed at', 'Moved reason'];
    const rows   = this.allTabInstances.map(i => [
      i.template.title,
      this.roleLabel(i.template.assignedRole),
      i.status,
      i.completedAt ? new Date(i.completedAt).toLocaleTimeString() : '',
      i.movedReason ?? '',
    ]);
    const csv = [header, ...rows].map(r => r.map(c => `"${c}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href = url; a.download = `tasks-${this.allTabDate}.csv`; a.click();
    URL.revokeObjectURL(url);
  }

  // ── Template helpers (used in template) ──────────────────────────────────

  typeIcon(type: string):  string { return this.typeIconMap[type]?.icon  ?? 'task_alt'; }
  typeBg(type:   string):  string { return this.typeIconMap[type]?.bg    ?? '#94a3b8'; }
  typeLabel(type: string): string { return this.typeLabelMap[type]       ?? type; }
  roleLabel(role: string): string { return this.roleLabelMap[role]       ?? role; }

  statusLabel(inst: TaskInstance): string {
    switch (inst.status) {
      case 'COMPLETED':              return `Completed ${new DatePipe('en').transform(inst.completedAt, 'h:mm a') ?? ''}`;
      case 'MOVED':                  return `Moved to ${this.formatDateDisplay(inst.movedToDate)}`;
      case 'AWAITING_CLARIFICATION': return 'Waiting for reply';
      default:                       return 'Pending';
    }
  }

  getClarifications(id: number): Clarification[] { return this.clarMap.get(id) ?? []; }

  getPanel(id: number): string | null       { return this.openPanel.get(id) ?? null; }
  getCannotReason(id: number): string       { return this.cannotReasons.get(id) ?? ''; }
  getQuestionText(id: number): string       { return this.questionTexts.get(id) ?? ''; }
  getReplyText(id: number): string          { return this.replyTexts.get(id) ?? ''; }
  getPastePreview(id: number): string       { return this.pastePreviews.get(id) ?? ''; }
  hasPasteFile(id: number): boolean         { return this.pasteFiles.has(id); }
  getQuestionPreview(id: number): string    { return this.questionPreviews.get(id) ?? ''; }
  hasQuestionImage(id: number): boolean     { return this.questionFiles.has(id); }
  isSubmitting(id: number): boolean         { return this.submitting.get(id) ?? false; }

  showClarPanel(inst: TaskInstance): boolean {
    if (this.closedThreads.has(inst.id)) return false;
    return inst.status === 'AWAITING_CLARIFICATION' || this.openPanel.get(inst.id) === 'question';
  }

  trackById(_: number, item: { id: number }): number { return item.id; }

  get allTabDateObj2(): Date { return new Date(this.allTabDate + 'T00:00:00'); }

  // ── Private utils ─────────────────────────────────────────────────────────

  private formatDateStr(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  formatDateDisplay(dateStr?: string): string {
    if (!dateStr) return '—';
    const d = new Date(dateStr + 'T00:00:00');
    return d.toLocaleDateString('en', { day: 'numeric', month: 'short' });
  }
}
